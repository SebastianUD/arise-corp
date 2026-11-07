
package com.arise.corp.controller;

import java.util.Map;
import java.util.LinkedHashMap;

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

        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("id", jwt.getSubject());
        identity.put("email", jwt.getClaimAsString("email"));
        identity.put("role", jwt.getClaimAsString("role"));
        return identity;
    }
}
