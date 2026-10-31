package com.ecommerce.notification.subscriber;

import com.ecommerce.notification.dto.OrderCreatedEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
@Slf4j
public class OrderEventSubscriber {

    @Bean
    public Consumer<OrderCreatedEventDTO> orderCreated(){
        return event -> {
            log.info("Received orderCreatedEvent for Order: {}", event.getOrderId());
            log.info("Received orderCreatedEvent for User ID: {}", event.getUserId());
        };
    }
}
