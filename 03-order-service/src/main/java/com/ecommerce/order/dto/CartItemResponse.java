package com.ecommerce.order.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItemResponse {

    private Long productId;
    private Integer quantity;
    private BigDecimal price;
}
