package com.ecommerce.order.service;

import com.ecommerce.order.client.ProductServiceClient;
import com.ecommerce.order.client.UserServiceClient;
import com.ecommerce.order.dto.CartItemRequest;
import com.ecommerce.order.dto.CartItemResponse;
import com.ecommerce.order.dto.ProductResponse;
import com.ecommerce.order.dto.UserResponse;
import com.ecommerce.order.entity.CartItem;
import com.ecommerce.order.repository.CartItemRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class CartItemService {

    private final CartItemRepository cartItemRepository;
    private final ProductServiceClient productServiceClient;
    private final UserServiceClient userServiceClient;

    private int retryCounter = 0;

    public CartItemService(CartItemRepository cartItemRepository, ProductServiceClient productServiceClient, UserServiceClient userServiceClient) {
        this.cartItemRepository = cartItemRepository;
        this.productServiceClient = productServiceClient;
        this.userServiceClient = userServiceClient;
    }

    //@CircuitBreaker(name = "productServiceCircuitBreaker")
    @Retry(name = "productServiceRetry", fallbackMethod = "addToCartFallback")
    public boolean addToCart(String userId, CartItemRequest cartItemRequest)
    {
        System.out.println("TRYING TO CONNECT ORDER SERVICE.... DENEME: " + retryCounter++);
        //Check if the product is valid
       ProductResponse productResponse = productServiceClient.getProductById(cartItemRequest.getProductId());
        if(productResponse == null) {
            return false;
        }

        //Get the Product and check the active and quantity
        if(productResponse.getStockQuantity() < cartItemRequest.getQuantity()) {
            return false;
        }

        //Check if the user is valid
        UserResponse userResponse = userServiceClient.getUserById(userId);
        if(userResponse == null) {
            return false;
        }

        //Check if user already has the item in the cart
        CartItem existingCartItem = cartItemRepository.findByUserIdAndProductId(userId, cartItemRequest.getProductId());

        if(existingCartItem != null) {
            existingCartItem.setQuantity(existingCartItem.getQuantity() + cartItemRequest.getQuantity());
            existingCartItem.setPrice(BigDecimal.valueOf(9999)); //It will be chanced when we learn interservice communication
            cartItemRepository.save(existingCartItem);
        }
        else {
            CartItem cartItem = new CartItem();
            cartItem.setProductId(cartItemRequest.getProductId());
            cartItem.setUserId(userId);
            cartItem.setQuantity(cartItemRequest.getQuantity());
            cartItem.setPrice(BigDecimal.valueOf(9999));
            cartItemRepository.save(cartItem);
        }

        return true;
    }

    public boolean addToCartFallback(String userId, CartItemRequest cartItemRequest, Exception ex) {
        return false;
    }

    public boolean deleteFromCart(String userId, String productId)
    {
        CartItem cartItem = cartItemRepository.findByUserIdAndProductId(userId, Long.valueOf(productId));

        if (cartItem != null) {
            cartItemRepository.delete(cartItem);
            return true;
        }
        return false;
    }

    public List<CartItemResponse> fetchCart(String userId)
    {
        return cartItemRepository.findByUserId(userId).stream()
                    .map(this::mapToCartResponse)
                    .collect(Collectors.toList());
    }

    public void clearCart(String userId) {
        cartItemRepository.deleteByUserId(userId);

    }

    private CartItemResponse mapToCartResponse(CartItem cartItem) {
        CartItemResponse cartItemResponse = new CartItemResponse();
        cartItemResponse.setProductId(cartItem.getProductId());
        cartItemResponse.setQuantity(cartItem.getQuantity());
        cartItemResponse.setPrice(cartItem.getPrice());
        return cartItemResponse;
    }
}
