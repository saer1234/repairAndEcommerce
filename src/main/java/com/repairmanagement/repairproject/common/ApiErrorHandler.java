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

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String,String>> invalidJson() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
            "error","Invalid request",
            "message","Some information is invalid. Check the account type and field formats."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> badRequest(MethodArgumentNotValidException ex) {
        // Response includes one human-readable reason and a stable map of field names.
        // Do not echo submitted passwords or other sensitive values.
        java.util.Map<String,String> fields = new java.util.LinkedHashMap<>();
        for (var violation : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(violation.getField(), violation.getDefaultMessage() == null
                ? "Invalid value" : violation.getDefaultMessage());
        }
        java.util.Map<String,Object> body = new java.util.LinkedHashMap<>();
        body.put("error", "Validation failed");
        body.put("message", "Please correct the highlighted fields.");
        body.put("fieldErrors", fields);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
