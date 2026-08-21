package com.halloween.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void catchAll_returnsGenericBodyWithoutInternalDetails() {
        ResponseEntity<Map<String, Object>> response = handler.handleException(new NullPointerException("boom-secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "Internal server error");
        assertThat(response.getBody().toString()).doesNotContain("boom-secret");
        assertThat(response.getBody().toString()).doesNotContain("NullPointerException");
    }

    @Test
    void jwtException_returns401() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleJwtException(new io.jsonwebtoken.JwtException("expired"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Invalid or expired token");
    }

    @Test
    void servletRequestBinding_returns400() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleServletRequestBinding(new ServletRequestBindingException("Authorization"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().toString()).doesNotContain("Authorization");
    }

    @Test
    void validation_returnsFieldLevelErrors() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(
                getClass().getDeclaredMethod("sampleEndpoint", String.class), 0);
        MapBindingResult bindingResult = new MapBindingResult(new HashMap<>(), "request");
        bindingResult.rejectValue("title", "NotBlank", "must not be blank");
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, String> fieldErrors = (Map<String, String>) response.getBody().get("fieldErrors");
        assertThat(fieldErrors).containsEntry("title", "must not be blank");
    }

    @Test
    void unreadableBody_returnsGenericMalformedRequestBody() {
        ResponseEntity<Map<String, Object>> response = handler.handleUnreadableMessage(
                new HttpMessageNotReadableException("JSON parse error details",
                        new RuntimeException(), new MockHttpInputMessage(new byte[0])));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Malformed request body");
        assertThat(response.getBody().toString()).doesNotContain("JSON parse error details");
    }

    @Test
    void dataIntegrityViolation_returns409WithGenericMessage() {
        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("constraint violation details"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "The request conflicts with existing data");
        assertThat(response.getBody().toString()).doesNotContain("constraint violation details");
    }

    @Test
    void illegalArgument_returns400WithoutRawMessage() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleIllegalArgumentException(new IllegalArgumentException("internal detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Bad request");
        assertThat(response.getBody().toString()).doesNotContain("internal detail");
    }

    @Test
    void responseStatus_keepsReasonAndStatus() {
        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuento no encontrado"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "Cuento no encontrado");
        assertThat(response.getBody()).containsEntry("status", 404);
    }

    void sampleEndpoint(String any) {
    }
}
