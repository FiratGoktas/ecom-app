package com.ecommerce.order.client;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    //Spring Boot 4 + Eureka + RestClient kombinasyonunda oluşan bir hatayı gidermek için iki farklı restclient builder yapmamız gerekiyor.
    //Birini Eureka service kullanacak diğerini kendi haberleşmemiz için atayacağız.
    @Bean
    @Primary
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
