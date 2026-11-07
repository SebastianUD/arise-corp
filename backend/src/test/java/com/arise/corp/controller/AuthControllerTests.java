package com.arise.corp.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder decoder;

    @Test
    void withoutTokenReturnsEmptyUnauthorized() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(content().string(""));
    }

    @Test
    void subjectOnlyIsAcceptedAndOptionalClaimsAreNull() throws Exception {
        mvc.perform(get("/api/me").with(jwt().jwt(token -> token.subject("test-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("test-user"))
                .andExpect(jsonPath("$.email").value(nullValue()))
                .andExpect(jsonPath("$.role").value(nullValue()));
    }

    @Test
    void returnsIdentityFromValidatedClaims() throws Exception {
        mvc.perform(get("/api/me").with(jwt().jwt(token -> token
                        .subject("test-user")
                        .claim("email", "paula@example.test")
                        .claim("role", "authenticated"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("test-user"))
                .andExpect(jsonPath("$.email").value("paula@example.test"))
                .andExpect(jsonPath("$.role").value("authenticated"));
    }

    @Test
    void invalidTokenIsRejectedBySecurityFilter() throws Exception {
        assertRejectedToken("test-invalid-token", "Invalid signature");
    }

    @Test
    void expiredTokenIsRejectedBySecurityFilter() throws Exception {
        assertRejectedToken("test-expired-token", "Expired token");
    }

    private void assertRejectedToken(String token, String reason) throws Exception {
        // Simula el rechazo del decoder; no consulta JWKS ni usa JWT reales.
        when(decoder.decode(token)).thenThrow(new BadJwtException(reason));
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(content().string(""));
        verify(decoder).decode(token);
    }
}
