package com.ecommerce.order.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductResponse {

    private Long id;
    private String name;
    private String description;
    private String category;
    private String imageUrl;
    private BigDecimal price;
    private Integer stockQuantity;
    private Boolean isActive;
}
