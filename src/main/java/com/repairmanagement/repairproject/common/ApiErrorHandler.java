package com.repairmanagement.repairproject.common;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> statusError(ResponseStatusException ex) {
        String reason = ex.getReason() != null ? ex.getReason() : "Request cannot be processed";
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
            "error", "Request rejected", "message", reason));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> badRequest(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
            "error", "Validation failed", "message", "Check required fields and field formats"));
    }
}
