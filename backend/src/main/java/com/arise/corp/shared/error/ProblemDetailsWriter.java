package com.arise.corp.shared.error;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Construye y escribe respuestas RFC 9457 Problem Details.
 *
 * <p>Se comparte entre el manejador global de excepciones y los handlers de
 * Spring Security, que se ejecutan fuera del ciclo de {@code DispatcherServlet}
 * y no pasan por {@code @RestControllerAdvice} (DP-API-02).
 */
@Component
public class ProblemDetailsWriter {

    private static final String CODIFICACION = "UTF-8";

    private final ObjectMapper objectMapper;

    public ProblemDetailsWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Crea un problema con la instancia tomada de la peticion actual.
     */
    public ProblemDetail crear(
            HttpStatus estado,
            String titulo,
            String detalle,
            HttpServletRequest request) {

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        problema.setInstance(URI.create(request.getRequestURI()));

        return problema;
    }

    /**
     * Escribe el problema con el tipo de contenido y el estado indicados.
     */
    public void escribir(
            ProblemDetail problema,
            HttpServletResponse response) throws IOException {

        response.setStatus(problema.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(CODIFICACION);
        objectMapper.writeValue(response.getOutputStream(), problema);
    }
}
