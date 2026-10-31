package com.ecommerce.order.controller;

import com.ecommerce.order.dto.CartItemRequest;
import com.ecommerce.order.dto.CartItemResponse;
import com.ecommerce.order.service.CartItemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartItemService cartItemService;

    public CartController(CartItemService cartItemService) {
        this.cartItemService = cartItemService;
    }

    @GetMapping
    public ResponseEntity<List<CartItemResponse>> fetchCart(@RequestHeader("X-User-ID") String userId) {
        return ResponseEntity.ok(cartItemService.fetchCart(userId));
    }

    @PostMapping
    public ResponseEntity<Void> addToCart(@RequestHeader("X-User-ID") String userId, @RequestBody CartItemRequest cartItemRequest)
    {
        if (cartItemService.addToCart(userId, cartItemRequest))
            return ResponseEntity.status(HttpStatus.CREATED).build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> deleteFromCart(@RequestHeader("X-User-ID") String userId, @PathVariable String productId)
    {
        boolean deleted = cartItemService.deleteFromCart(userId, productId);
        return deleted ? ResponseEntity.status(HttpStatus.OK).build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
