package com.ecommerce.order.service;

import com.ecommerce.order.client.UserServiceClient;
import com.ecommerce.order.dto.*;
import com.ecommerce.order.entity.*;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemService cartItemService;
    private final UserServiceClient userServiceClient;
    private final StreamBridge streamBridge;

    public Optional<OrderResponse> createOrder(String userId) {

        //Validate the User
        UserResponse userResponse = userServiceClient.getUserById(userId);
        if(userResponse == null) {
            return Optional.empty();
        }

        //Validate the cart is not empty
        List<CartItemResponse> cartItems = cartItemService.fetchCart(userId);
        if(cartItems.isEmpty()) {
            return Optional.empty();
        }

        //Calculate Total Price
        BigDecimal totalPrice = cartItems.stream()
                .map(item -> item.getPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        //Create Order
        Order order = new Order();
        order.setUserId(userId);
        order.setTotalAmount(totalPrice);
        order.setStatus(OrderStatus.CONFIRMED);
        List<OrderItem> orderItems = cartItems.stream()
                        .map(item -> {
                            OrderItem orderItem = new OrderItem();
                            orderItem.setPrice(item.getPrice());
                            orderItem.setQuantity(item.getQuantity());
                            orderItem.setProductId(item.getProductId());
                            orderItem.setOrder(order);
                            return orderItem;
                        }).toList();
        order.setItems(orderItems);
        Order savedOrder = orderRepository.save(order);

        //Clear the Cart
        cartItemService.clearCart(userId);

        //creating a DTO and send it with rabbitmq queue message for Notification Service
        OrderCreatedEventDTO eventMessage = new OrderCreatedEventDTO(
                savedOrder.getId(),
                savedOrder.getUserId(),
                savedOrder.getStatus(),
                savedOrder.getItems()
                        .stream()
                        .map(this::mapToOrderItemDTO)
                        .toList(),
                savedOrder.getTotalAmount(),
                savedOrder.getCreatedAt(),
                savedOrder.getUpdatedAt()
        );

        streamBridge.send("createOrder-out-0", eventMessage);

        //Return Optional<OrderResponse>
        return Optional.of(mapToOrderResponse(savedOrder));
    }

    private OrderResponse mapToOrderResponse(Order order) {
        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setId(order.getId());
        orderResponse.setStatus(order.getStatus());
        orderResponse.setItems(order.getItems().stream().
                map(this::mapToOrderItemDTO).toList());
        orderResponse.setTotalAmount(order.getTotalAmount());
        orderResponse.setCreatedAt(order.getCreatedAt());
        orderResponse.setUpdatedAt(order.getUpdatedAt());
        return orderResponse;
    }

    private OrderItemDTO mapToOrderItemDTO(OrderItem orderItem){
        return new OrderItemDTO(
                orderItem.getId(),
                orderItem.getProductId(),
                orderItem.getQuantity(),
                orderItem.getPrice(),
                orderItem.getPrice().multiply(new BigDecimal(orderItem.getQuantity()))
        );
    }
}
