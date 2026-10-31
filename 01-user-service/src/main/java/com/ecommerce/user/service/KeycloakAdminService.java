package com.ecommerce.user.service;

import com.ecommerce.user.dto.UserRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.type.ClassStack;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class KeycloakAdminService {

    @Value("${keycloak.admin.username}")
    private String adminUsername;
    @Value("${keycloak.admin.password}")
    private String adminPassword;
    @Value("${keycloak.admin.server-url}")
    private String keycloakServerUrl;
    @Value("${keycloak.admin.realm}")
    private String realm;
    @Value("${keycloak.admin.client-id}")
    private String clientId;

    private final RestTemplate restTemplate = new RestTemplate();


    public String getAdminAccessToken(){
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", clientId);
        params.add("username", adminUsername);
        params.add("password", adminPassword);
        params.add("grant_type", "password");

        HttpHeaders header = new HttpHeaders();
        header.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, header);

        ResponseEntity<Map> response = restTemplate.postForEntity("http://localhost:8443/realms/ecom-app/protocol/openid-connect/token", entity, Map.class);
        return response.getBody().get("access_token").toString();
    }

    public String createkeycloakUser(String token, UserRequest userRequest){

        HttpHeaders header = new HttpHeaders();
        header.setContentType(MediaType.APPLICATION_JSON);
        header.setBearerAuth(token);

        Map<String, Object> userPayload = new HashMap<>();
        userPayload.put("username", userRequest.getFirstName());
        userPayload.put("email", userRequest.getEmail());
        userPayload.put("enabled", true);
        userPayload.put("firstName", userRequest.getFirstName());
        userPayload.put("lastName", userRequest.getLastName());

        Map<String, Object> credentials = new HashMap<>();
        credentials.put("type", "password");
        credentials.put("value", "user");
        credentials.put("temporary", false);
        userPayload.put("credentials", List.of(credentials));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(userPayload, header);
        String url = "http://localhost:8443/admin/realms/ecom-app/users";

        ResponseEntity<String> response = restTemplate.postForEntity(
                url,
                entity,
                String.class
        );

        if(!HttpStatus.CREATED.equals(response.getStatusCode())){
            throw new RuntimeException("Failed to create user in Keycloak: " + response.getBody());
        }

        URI location = response.getHeaders().getLocation();
        if(location == null){
            throw new RuntimeException("No LOcation in Keycloak " + response.getBody());
        }

        String path = location.getPath();
        return path.substring(path.lastIndexOf("/") + 1);
    }
}
