package com.ecommerce.product.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductRequest {

    private String name;
    private String description;
    private String category;
    private String imageUrl;
    private BigDecimal price;
    private Integer stockQuantity;
}
