package com.arise.corp.shared.error;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce excepciones de la API a RFC 9457 Problem Details.
 *
 * <p>Formato adoptado en DP-API-02: {@code application/problem+json} con
 * {@code type}, {@code title}, {@code status}, {@code detail} e
 * {@code instance}. Las validaciones por campo agregan la extension
 * {@code errors} con {@code field} y {@code message}. Los errores inesperados
 * responden un mensaje generico y nunca exponen trazas, SQL ni datos internos
 * (AUTH-05, CA-06).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problema = problema(
                status,
                "Solicitud inválida",
                "La solicitud contiene campos inválidos.",
                request);

        List<Map<String, String>> errores = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", mensaje(error.getDefaultMessage())))
                .toList();

        problema.setProperty("errors", errores);

        return handleExceptionInternal(ex, problema, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problema = problema(
                status,
                "Solicitud inválida",
                "Los parámetros de la solicitud no son válidos.",
                request);

        List<Map<String, String>> errores = ex.getParameterValidationResults()
                .stream()
                .flatMap(resultado -> resultado.getResolvableErrors().stream()
                        .map(error -> Map.of(
                                "field", parametro(resultado),
                                "message", mensaje(error.getDefaultMessage()))))
                .toList();

        problema.setProperty("errors", errores);

        return handleExceptionInternal(ex, problema, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problema = problema(
                status,
                "Solicitud inválida",
                "El cuerpo de la solicitud no es válido.",
                request);

        return handleExceptionInternal(ex, problema, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problema = problema(
                status,
                "Recurso no encontrado",
                "El recurso solicitado no existe.",
                request);

        return handleExceptionInternal(ex, problema, headers, status, request);
    }

    /**
     * Recurso de negocio inexistente (404).
     */
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Object> handleRecursoNoEncontrado(
            RecursoNoEncontradoException ex,
            WebRequest request) {

        ProblemDetail problema = problema(
                HttpStatus.NOT_FOUND,
                "Recurso no encontrado",
                ex.getMessage() != null
                        ? ex.getMessage()
                        : "El recurso solicitado no existe.",
                request);

        return handleExceptionInternal(
                ex, problema, new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }

    /**
     * Un perfil ausente o inactivo detectado en el servicio tambien responde
     * 403 con el mismo formato.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(
            AccessDeniedException ex,
            WebRequest request) {

        ProblemDetail problema = problema(
                HttpStatus.FORBIDDEN,
                "Acceso denegado",
                "No tienes permisos para realizar esta operación.",
                request);

        return handleExceptionInternal(
                ex, problema, new HttpHeaders(), HttpStatus.FORBIDDEN, request);
    }

    /**
     * Las restricciones de PostgreSQL son la ultima defensa (NF-03); el
     * mensaje de la base nunca se devuelve al cliente.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            WebRequest request) {

        LOGGER.warn("Conflicto de integridad de datos al procesar una solicitud.");

        ProblemDetail problema = problema(
                HttpStatus.CONFLICT,
                "Conflicto",
                "La operación entra en conflicto con el estado actual del recurso.",
                request);

        return handleExceptionInternal(
                ex, problema, new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(
            Exception ex,
            WebRequest request) {

        LOGGER.error("Error inesperado al procesar una solicitud.", ex);

        ProblemDetail problema = problema(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno",
                "Ocurrió un error inesperado. Intenta nuevamente.",
                request);

        return handleExceptionInternal(
                ex,
                problema,
                new HttpHeaders(),
                HttpStatus.INTERNAL_SERVER_ERROR,
                request);
    }

    private ProblemDetail problema(
            HttpStatusCode status,
            String titulo,
            String detalle,
            WebRequest request) {

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
        problema.setTitle(titulo);

        URI instancia = instancia(request);
        if (instancia != null) {
            problema.setInstance(instancia);
        }

        return problema;
    }

    private URI instancia(WebRequest request) {
        String descripcion = request.getDescription(false);

        if (descripcion != null && descripcion.startsWith("uri=")) {
            return URI.create(descripcion.substring("uri=".length()));
        }

        return null;
    }

    private String parametro(ParameterValidationResult resultado) {
        String nombre = resultado.getMethodParameter().getParameterName();
        return nombre != null ? nombre : "solicitud";
    }

    private String mensaje(String mensaje) {
        return mensaje != null ? mensaje : "valor inválido";
    }
}
