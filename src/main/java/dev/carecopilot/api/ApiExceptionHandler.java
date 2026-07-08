package dev.carecopilot.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiError> illegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
    return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException exception, HttpServletRequest request) {
    String message = exception.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(error -> error.getField() + " " + error.getDefaultMessage())
        .orElse("Invalid request body.");
    return error(HttpStatus.BAD_REQUEST, message, request);
  }

  private ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest request) {
    return ResponseEntity.status(status).body(new ApiError(
        Instant.now(),
        status.value(),
        status.getReasonPhrase(),
        message,
        request.getRequestURI()));
  }
}
