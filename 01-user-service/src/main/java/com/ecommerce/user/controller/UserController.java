package com.ecommerce.user.controller;

import com.ecommerce.user.dto.UserRequest;
import com.ecommerce.user.dto.UserResponse;
import com.ecommerce.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class UserController {

    private UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/api/users")
    public List<UserResponse> getUserList()
    {
        return userService.fetchAllUsers();
    }

    @GetMapping("/api/users/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable String id)
    {
        return userService.findUserById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/api/users")
    public String createUser(@RequestBody UserRequest userRequest)
    {
        userService.addUser(userRequest);
        return "User added successfully...";
    }

    @PutMapping("/api/users/{id}")
    public String updateUserById(@PathVariable String id, @RequestBody UserRequest userRequest)
    {
        if(userService.updateUser(userRequest, id))
            return "User Updated Successfully";
        return "User Not Found";
    }
}
