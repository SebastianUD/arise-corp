
package com.arise.corp.controller;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    @GetMapping("/me")
    public Map<String, Object> getCurrentUser(
            @AuthenticationPrincipal Jwt jwt) {

        return Map.of(
                "id", jwt.getSubject(),
                "email", jwt.getClaimAsString("email"),
                "role", jwt.getClaimAsString("role")
        );
    }
}
