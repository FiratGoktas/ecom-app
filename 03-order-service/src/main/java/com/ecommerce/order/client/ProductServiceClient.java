package com.ecommerce.order.client;

import com.ecommerce.order.dto.ProductResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/api/products")
public interface ProductServiceClient {

    @GetExchange("/{id}")
    ProductResponse getProductById(@PathVariable Long id);
}
