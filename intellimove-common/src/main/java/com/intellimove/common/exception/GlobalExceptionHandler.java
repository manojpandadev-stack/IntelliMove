package com.intellimove.common.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {} at {}", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        log.warn("Business error: {} at {}", ex.getMessage(), request.getRequestURI());
        Map<String, Object> details = Map.of("errorCode", ex.getErrorCode());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(),
                request.getRequestURI(), details);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidStateTransition(
            InvalidStateTransitionException ex, HttpServletRequest request) {
        log.warn("Invalid state transition: {} at {}", ex.getMessage(), request.getRequestURI());
        Map<String, Object> details = Map.of("errorCode", ex.getErrorCode());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(),
                request.getRequestURI(), details);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("Validation failed at {}: {}", request.getRequestURI(), errors);

        Map<String, Object> details = Map.of("fieldErrors", errors);
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed",
                request.getRequestURI(), details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, Object> details = Map.of("violations", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Constraint violation",
                request.getRequestURI(), details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequestBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        String detail = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        log.warn("Bad request body at {}: {}", request.getRequestURI(), detail);
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid request body: " + detail,
                                request.getRequestURI(), null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(),
                request.getRequestURI(), null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String param = ex.getName();
        String reason = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();
        log.warn("Type mismatch for parameter '{}' at {}: {}", param, request.getRequestURI(), reason);
        Map<String, Object> details = Map.of("parameter", param, "reason", reason);
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Invalid value for parameter '" + param + "'",
                request.getRequestURI(), details);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(
            UnauthorizedException ex, HttpServletRequest request) {
        log.warn("Unauthorized: {} at {}", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(),
                request.getRequestURI(), null);
    }

    @ExceptionHandler(DuplicateEventException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateEvent(
            DuplicateEventException ex, HttpServletRequest request) {
        log.debug("Duplicate event skipped: {}", ex.getMessage());
        return buildResponse(HttpStatus.OK, "Event already processed",
                request.getRequestURI(), Map.of("duplicate", true));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/actuator")) {
            log.debug("Actuator media type issue at {}: {}", uri, ex.getMessage());
        } else {
            log.warn("Media type not acceptable at {}: {}", uri, ex.getMessage());
        }
        return buildResponse(HttpStatus.NOT_ACCEPTABLE,
                "Not Acceptable", uri, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("Upload size exceeded at {}: {}", request.getRequestURI(), ex.getMessage());
        Map<String, Object> details = Map.of("errorCode", "PHOTO_TOO_LARGE");
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY,
                "The uploaded file is too large.", request.getRequestURI(), details);
    }

    /**
     * Spring Security method-security denials (@PreAuthorize / AuthorizationDeniedException)
     * are thrown INSIDE the DispatcherServlet, so without this handler they fall into
     * the generic Exception handler above and are misreported as HTTP 500.
     * A role-based denial must always be 403 Forbidden.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {} at {}", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied", request.getRequestURI(), null);
    }

    /** A request sent with an unsupported HTTP method is a client error (405), not a 500. */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("Method not supported: {} at {}", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed",
                request.getRequestURI(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(
            Exception ex, HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/actuator")) {
            log.debug("Actuator error at {}: {}", uri, ex.getMessage());
            return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal error", uri, null);
        }
        log.error("Unexpected error at {}: {}", uri, ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred", uri, null);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status, String message, String path, Map<String, Object> details) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", path);
        body.put("traceId", UUID.randomUUID().toString());
        if (details != null) {
            body.put("details", details);
        }
        return ResponseEntity.status(status).body(body);
    }
}
