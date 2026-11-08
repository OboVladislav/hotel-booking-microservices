package com.example.hotel;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MissingRequestHeaderException;
@RestControllerAdvice
public class ApiExceptionHandler {
 public record ApiError(Instant time,int status,String message) {}
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<ApiError> status(ResponseStatusException ex) {
  return ResponseEntity.status(ex.getStatusCode()).body(new ApiError(Instant.now(),ex.getStatusCode().value(),ex.getReason()));
 }
 @ExceptionHandler({MethodArgumentNotValidException.class,IllegalArgumentException.class,MissingRequestHeaderException.class})
 ResponseEntity<ApiError> badRequest(Exception ex) {
  return ResponseEntity.badRequest().body(new ApiError(Instant.now(),400,ex.getMessage()));
 }
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ApiError> conflict(DataIntegrityViolationException ex) {
  return ResponseEntity.status(409).body(new ApiError(Instant.now(),409,"Data conflict"));
 }
}
