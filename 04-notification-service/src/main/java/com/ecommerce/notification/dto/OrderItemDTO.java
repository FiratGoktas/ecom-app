package com.ecommerce.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class OrderItemDTO {

    private Long id;
    private Long product_id;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal subtotal;

}
