package com.ecommerce.notification.subscriber;

import com.ecommerce.notification.dto.OrderCreatedEventDTO;
import com.ecommerce.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderEventSubscriber {

    private final EmailService emailService;

    @Bean
    public Consumer<OrderCreatedEventDTO> orderCreated(){
        return event -> {
            log.info("Received orderCreatedEvent for Order: {}", event.getOrderId());
            log.info("Received orderCreatedEvent for User ID: {}", event.getUserId());
            emailService.sendOrderConfirmation(event);
        };
    }

    /**
     * Re-processes events whose email failed 3 times on the main topic (topic order.exchange.notification-dlq).
     * The binding retries slowly with a long backoff (see notification-service.yml), so emails go out once the
     * mail server is back, and new orders on the main topic aren't blocked meanwhile.
     */
    @Bean
    public Consumer<OrderCreatedEventDTO> orderCreatedDlq(){
        return event -> {
            log.info("Retrying confirmation email from DLQ for Order: {}", event.getOrderId());
            emailService.sendOrderConfirmation(event);
        };
    }
}
