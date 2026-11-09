package com.arise.corp.shared.error;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Respuesta 403 uniforme cuando Spring Security deniega una ruta.
 *
 * <p>Se aplica antes de ejecutar el controlador, de modo que la operacion no
 * se ejecuta ni modifica datos (CA-03, CA-06).
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailsWriter problemDetailsWriter;

    public RestAccessDeniedHandler(
            ProblemDetailsWriter problemDetailsWriter) {

        this.problemDetailsWriter = problemDetailsWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        ProblemDetail problema = problemDetailsWriter.crear(
                HttpStatus.FORBIDDEN,
                "Acceso denegado",
                "No tienes permisos para realizar esta operación.",
                request);

        problemDetailsWriter.escribir(problema, response);
    }
}
