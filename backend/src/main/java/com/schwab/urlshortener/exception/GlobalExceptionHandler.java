package com.schwab.urlshortener.exception;

import com.schwab.urlshortener.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(CapacityExceededException.class)
  public ResponseEntity<ApiResponse<Void>> handleCapacityExceeded(CapacityExceededException ex) {
    log.warn("Capacity limit reached: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ApiResponse.error(ex.getMessage(), "CAPACITY_EXCEEDED"));
  }

  @ExceptionHandler(UrlNotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleUrlNotFound(UrlNotFoundException ex) {
    log.warn("URL not found: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error(ex.getMessage(), "URL_NOT_FOUND"));
  }

  @ExceptionHandler(UrlExpiredException.class)
  public ResponseEntity<ApiResponse<Void>> handleUrlExpired(UrlExpiredException ex) {
    log.warn("URL expired: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.GONE)
        .body(ApiResponse.error(ex.getMessage(), "URL_EXPIRED"));
  }

  @ExceptionHandler(DuplicateAliasException.class)
  public ResponseEntity<ApiResponse<Void>> handleDuplicateAlias(DuplicateAliasException ex) {
    log.warn("Duplicate alias: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.error(ex.getMessage(), "DUPLICATE_ALIAS"));
  }

  @ExceptionHandler(PolicyViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handlePolicyViolation(PolicyViolationException ex) {
    log.warn("Policy violation: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(ApiResponse.error(ex.getMessage(), "POLICY_VIOLATION"));
  }

  @ExceptionHandler(OrchestrationException.class)
  public ResponseEntity<ApiResponse<Void>> handleOrchestration(OrchestrationException ex) {
    log.error("Orchestration error: {}", ex.getMessage(), ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error(ex.getMessage(), "ORCHESTRATION_ERROR"));
  }

  @ExceptionHandler(ApprovalRequiredException.class)
  public ResponseEntity<ApiResponse<Void>> handleApprovalRequired(ApprovalRequiredException ex) {
    log.info("Approval required: runId={}, taskId={}", ex.getRunId(), ex.getTaskId());
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.error(ex.getMessage(), "APPROVAL_REQUIRED"));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationErrors(
      MethodArgumentNotValidException ex) {
    String fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));
    log.warn("Validation failed: {}", fieldErrors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("Validation failed: " + fieldErrors, "VALIDATION_ERROR"));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
      ConstraintViolationException ex) {
    return ResponseEntity.badRequest()
        .body(ApiResponse.error("Request constraint validation failed", "VALIDATION_ERROR"));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnreadableRequest(
      HttpMessageNotReadableException ex) {
    return ResponseEntity.badRequest()
        .body(ApiResponse.error("Malformed request body", "MALFORMED_REQUEST"));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
    log.warn("Database constraint rejected request: {}", ex.getMostSpecificCause().getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ApiResponse.error("The requested value conflicts with existing data", "DATA_CONFLICT"));
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(
      ObjectOptimisticLockingFailureException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.error("The resource changed; refresh and retry", "CONCURRENT_UPDATE"));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
    log.warn("Illegal argument: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(ex.getMessage(), "BAD_REQUEST"));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
    log.warn("Invalid state transition: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.error(ex.getMessage(), "INVALID_STATE"));
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(ApiResponse.error("Invalid username or password", "INVALID_CREDENTIALS"));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ApiResponse.error("Access denied", "ACCESS_DENIED"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
    log.error("Unexpected error: {}", ex.getMessage(), ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error("An unexpected error occurred", "INTERNAL_SERVER_ERROR"));
  }
}
