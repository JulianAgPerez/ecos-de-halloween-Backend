package com.halloween.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException e) {
        return ResponseEntity
                .status(e.getStatusCode())
                .body(Map.of("error", e.getReason() != null ? e.getReason() : "Error"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity
                .badRequest()
                .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Bad request"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoResourceFoundException(NoResourceFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Not found"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Map.of("error", "Method not allowed"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }
}
