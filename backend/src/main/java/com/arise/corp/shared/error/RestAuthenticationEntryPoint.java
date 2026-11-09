package com.arise.corp.shared.error;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Respuesta 401 uniforme para peticiones sin token o con token rechazado.
 *
 * <p>Conserva el encabezado {@code WWW-Authenticate} exigido por el esquema
 * Bearer y no expone el motivo tecnico del rechazo (AUTH-03, CA-02, CA-06).
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailsWriter problemDetailsWriter;

    public RestAuthenticationEntryPoint(
            ProblemDetailsWriter problemDetailsWriter) {

        this.problemDetailsWriter = problemDetailsWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");

        ProblemDetail problema = problemDetailsWriter.crear(
                HttpStatus.UNAUTHORIZED,
                "No autenticado",
                "Se requiere un token válido para acceder a este recurso.",
                request);

        problemDetailsWriter.escribir(problema, response);
    }
}
