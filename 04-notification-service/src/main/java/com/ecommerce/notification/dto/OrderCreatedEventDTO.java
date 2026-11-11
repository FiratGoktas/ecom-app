package com.ecommerce.notification.dto;

import com.ecommerce.notification.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEventDTO {

    private Long orderId;
    private String userId;
    private String userEmail;
    private String userFirstName;
    private String userLastName;
    private OrderStatus orderStatus;
    List<OrderItemDTO> items;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
