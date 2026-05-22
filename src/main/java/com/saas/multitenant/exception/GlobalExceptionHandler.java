package com.saas.multitenant.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import java.net.URI;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TenantNotFoundException.class)
    public ProblemDetail handleTenantNotFound(TenantNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://errors.saas.example/tenant-not-found"));
        pd.setTitle("Tenant Not Found");
        return pd;
    }

    @ExceptionHandler(TenantSuspendedException.class)
    public ProblemDetail handleSuspended(TenantSuspendedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setType(URI.create("https://errors.saas.example/tenant-suspended"));
        pd.setTitle("Tenant Suspended");
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        String message = ex.getMessage();
        HttpStatus status = HttpStatus.BAD_REQUEST;

        if (message != null && message.contains("already exists")) {
            status = HttpStatus.CONFLICT; // 409
        } else if (message != null && message.contains("not found")) {
            status = HttpStatus.NOT_FOUND; // 404
        }

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, message);
        pd.setType(URI.create("https://errors.saas.example/invalid-request"));
        pd.setTitle(status == HttpStatus.CONFLICT ? "Conflict"
                : status == HttpStatus.NOT_FOUND ? "Not Found" : "Bad Request");
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, details);
        pd.setType(URI.create("https://errors.saas.example/validation-failed"));
        pd.setTitle("Validation Failed");
        return pd;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        String message = ex.getMessage() != null && ex.getMessage().contains("Duplicate")
                ? "A record with this identifier already exists."
                : "A database constraint was violated.";

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, message);
        pd.setType(URI.create("https://errors.saas.example/conflict"));
        pd.setTitle("Conflict");
        return pd;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
        pd.setType(URI.create("https://errors.saas.example/forbidden"));
        pd.setTitle("Forbidden");
        return pd;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://errors.saas.example/resource-not-found"));
        pd.setTitle("Resource Not Found");
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        pd.setType(URI.create("https://errors.saas.example/internal-error"));
        pd.setTitle("Internal Server Error");
        return pd;
    }
}