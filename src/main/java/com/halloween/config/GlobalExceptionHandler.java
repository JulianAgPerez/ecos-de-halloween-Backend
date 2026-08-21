package com.halloween.config;

import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException e) {
        return build(e.getStatusCode().value(), e.getReason() != null ? e.getReason() : "Error");
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<Map<String, Object>> handleJwtException(JwtException e) {
        log.warn("Rejected invalid JWT: {}", e.getClass().getSimpleName());
        return build(HttpStatus.UNAUTHORIZED.value(), "Invalid or expired token");
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<Map<String, Object>> handleServletRequestBinding(ServletRequestBindingException e) {
        return build(HttpStatus.BAD_REQUEST.value(), "Missing or invalid request header");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        // Messages come from our own constraint annotations, so they are safe to expose.
        Map<String, String> fieldErrors = new HashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        Map<String, Object> body = errorBody(HttpStatus.BAD_REQUEST.value(), "Validation failed");
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableMessage(HttpMessageNotReadableException e) {
        log.warn("Malformed request body: {}", e.getMessage());
        return build(HttpStatus.BAD_REQUEST.value(), "Malformed request body");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("Data integrity violation", e);
        return build(HttpStatus.CONFLICT.value(), "The request conflicts with existing data");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Rejected request: {}", e.getMessage());
        return build(HttpStatus.BAD_REQUEST.value(), "Bad request");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFoundException(NoResourceFoundException e) {
        return build(HttpStatus.NOT_FOUND.value(), "Not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return build(HttpStatus.METHOD_NOT_ALLOWED.value(), "Method not supported for this route");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal server error");
    }

    private static Map<String, Object> errorBody(int status, String error) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", error);
        body.put("status", status);
        return body;
    }

    private static ResponseEntity<Map<String, Object>> build(int status, String error) {
        return ResponseEntity.status(status).body(errorBody(status, error));
    }
}
