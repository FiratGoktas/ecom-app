package com.ecommerce.user.entity;

import lombok.Data;

@Data
public class Address {


    private Long id;
    private String street;
    private String city;
    private String state;
    private String country;
    private String zipcode;

    private User user;

    public Address(String street, String city, String state, String country, String zipcode) {
        this.street = street;
        this.city = city;
        this.state = state;
        this.country = country;
        this.zipcode = zipcode;
    }

    public Address(){
    }

}
