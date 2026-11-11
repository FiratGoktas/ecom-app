# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview
Spring Boot e-commerce backend built as microservices. Each numbered folder is an **independent Maven project** with its own `mvnw`. There is no parent POM, so build and run each service from inside its own folder. Stack: Java 25, Spring Boot 4.1, Spring Cloud 2025.1.x, Lombok. Some code comments are in Turkish.

## Commands
Run these from inside a service folder (e.g. `cd 03-order-service`). On Windows, use `mvnw.cmd` or `./mvnw`.
- Build: `./mvnw clean package` (add `-DskipTests` to skip tests)
- Run: `./mvnw spring-boot:run`
- All tests: `./mvnw test`
- Single test: `./mvnw test -Dtest=ClassName#methodName`
- Infra: `docker compose up -d` (repo root) starts Postgres 14 (5432, user/pass `springstudent`), pgAdmin (5050), RabbitMQ (5672 / UI 15672) and Mailpit (fake SMTP on 1025, inbox UI at http://localhost:8025).

Most tests are the generated `*ApplicationTests` context-load tests, which need the config server and the infra running. The exception is `04-notification-service`'s `EmailServiceTest`, a plain unit test (`./mvnw test -Dtest=EmailServiceTest`) that needs no infra.

## Runtime dependencies not in docker-compose
The services also need these, which you must start yourself: **MongoDB** (27017, user-service DB `user_table`), **Kafka** (9092), **Keycloak** (8443, realm `ecom-app`) and Zipkin (gateway tracing). Postgres needs the databases `product_db` and `order_db` to exist. Hibernate uses `ddl-auto: update`, so it creates the tables but not the databases.

product-service and order-service read `DB_USERNAME`/`DB_PASSWORD` from the environment. Each has a gitignored `.env` file holding them, and that file isn't loaded automatically.

## Startup order
`05-config-server` (8888) → `06-eureka-server` (8761) → services (user 8082, product 8081, order 8083, notification 8084) → `07-api-gateway` (8080).

## Architecture

```
                    Keycloak (8443, realm ecom-app) ── issues JWTs
                                │
client ──JWT──▶ api-gateway (8080) ── validates JWT, routes lb://<SERVICE> via Eureka
                    │
      ┌─────────────┼──────────────────────┐
      ▼             ▼                      ▼
 user-service   product-service       order-service ──REST (lb)──▶ user-service, product-service
 (8082, Mongo)  (8081, Postgres)      (8083, Postgres)
      │                                    │ Kafka topic "order.exchange" (OrderCreated)
      ▼                                    ▼
 Keycloak admin API              notification-service (8084) ──SMTP──▶ Mailpit (1025)

 Shared infra: config-server (8888, native files + Bus/RabbitMQ) · eureka-server (8761)
```

| Service | Responsibility | Endpoints |
|---|---|---|
| user-service | User CRUD, creates matching Keycloak user | `/api/users` |
| product-service | Product CRUD + search | `/api/products` |
| order-service | Cart and checkout, publishes OrderCreated | `/api/cart`, `/api/orders` |
| notification-service | Consumes OrderCreated, emails an order confirmation (no HTTP API) | — |

- **Centralized config**: each service's own `application.yml` only sets `spring.application.name` and `spring.config.import: optional:configserver:http://localhost:8888`. **All real config (ports, DBs, Eureka, Resilience4j, Kafka, gateway routes, Keycloak) lives in `05-config-server/src/main/resources/config/<app-name>.yml`.** Edit those files, not the service's own yml. The config server runs the `native` profile with an **absolute Windows path** in `search-locations`. Spring Cloud Bus over RabbitMQ handles `/actuator/busrefresh`.
- **Gateway** (`07-api-gateway`, WebFlux):
  - Routes `/api/products/**`, `/api/users/**`, `/api/orders/**` and `/api/cart/**` to `lb://<SERVICE>` through Eureka.
  - Acts as an OAuth2 resource server that validates Keycloak JWTs.
  - `security/SecurityConfig.java` maps roles from the `resource_access.oauth2-pkce.roles` claim to `ROLE_*`. Path rules need PRODUCT, ORDER or USER.
- **User identity downstream**: order-service endpoints (cart and orders) take the user id from the `X-User-ID` request header. The gateway doesn't derive it from the JWT; the client sends it.
- **Inter-service REST (order-service)**: declarative `@HttpExchange` interfaces in `client/` (`ProductServiceClient`, `UserServiceClient`), built through `HttpServiceProxyFactory` on a `@LoadBalanced RestClient.Builder` with base URLs `http://product-service` and `http://user-service`. `RestClientConfig` deliberately defines two builders (a `@Primary` plain one and a `@LoadBalanced` one) to work around a Spring Boot 4 + Eureka issue. Keep both. `CartItemService` uses Resilience4j `@Retry(productServiceRetry)` with a fallback method.
- **Events**:
  - order-service publishes `OrderCreatedEventDTO` via `StreamBridge` to binding `createOrder-out-0` (Kafka topic `order.exchange`). It sends **only after the DB transaction commits** (`TransactionSynchronization.afterCommit` in `OrderService`), so a rolled-back order never produces an email. Keep it that way.
  - The event carries the user's `userEmail`, `userFirstName` and `userLastName`. order-service copies them from the user-service response it already fetches at checkout, so notification-service never calls user-service.
  - notification-service consumes it with the functional `Consumer` bean `orderCreated` (`orderCreated-in-0`), in consumer group `notification-service` with `start-offset: latest`. Without the named group, events sent while the service is down are lost and every instance sends every email. Without `latest`, a new group would replay and email every past order.
  - The DTOs are duplicated in each service (no shared module), so change both sides together. Deploy the consumer first, and keep it tolerant of missing fields, because old events stay on the topic.
  - Messaging is Kafka; RabbitMQ is used only for Spring Cloud Bus.
- **notification-service email**: `service/EmailService` renders the Thymeleaf template `templates/order-confirmation.html` and sends it with `JavaMailSender`. SMTP settings and `notification.mail.from` are in the config-server yml.
  - Error handling is deliberate. Permanent failures (no address, bad address, template error) are logged and skipped. Transient `MailException`s are **rethrown**, so the binder retries 3 times and then sends the message to the DLQ topic `order.exchange.notification-dlq`. Don't catch everything.
  - A second consumer bean, `orderCreatedDlq` (group `notification-service-dlq`, start offset `earliest`), re-sends DLQ messages through the same `EmailService`. It uses a slow backoff: 5s doubling to 5 min, 20 attempts, about 70 min in total. Emails go out on their own once SMTP is back, without blocking new orders on the main topic.
  - Messages that still fail after that go to the parking topic `order.exchange.notification-parking`, for inspection or replay by hand.
  - Because those retries block that consumer's poll loop, its binding sets `max.poll.records: 1` and `max.poll.interval.ms: 5400000` (90 min). If you lengthen the backoff, raise the poll interval too.
  - Both function names are listed in `spring.cloud.function.definition` and `spring.cloud.stream.function.definition` (`orderCreated;orderCreatedDlq`). Add new consumers to both.
  - Delivery is at-least-once: a retry after SMTP accepted the mail but failed to confirm sends a duplicate. This is accepted for now.
  - SMTP timeouts (5s) are required. JavaMail's default is infinite, and a hung send would stall the Kafka consumer.
  - The template uses `th:text` only, because user names are user input. The email shows product IDs, not names, since the event has no product names.
- **user-service**: MongoDB persistence. `KeycloakAdminService` gets an admin token and creates the user in Keycloak when a user is created.
- Layering inside each service: `controller` → `service` → `repository`, with `dto` request/response objects and `entity`. Mapping is done by hand in the services.
