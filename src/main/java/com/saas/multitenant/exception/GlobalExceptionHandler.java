package com.saas.multitenant.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;


//  Translates domain exceptions into structured RFC 7807 Problem Detail responses.

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler 
{

    @ExceptionHandler(TenantNotFoundException.class)
    public ProblemDetail handleTenantNotFound(TenantNotFoundException ex) 
    {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://errors.saas.example/tenant-not-found"));
        pd.setTitle("Tenant Not Found");
        return pd;
    }

    @ExceptionHandler(TenantSuspendedException.class)
    public ProblemDetail handleSuspended(TenantSuspendedException ex) 
    {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setType(URI.create("https://errors.saas.example/tenant-suspended"));
        pd.setTitle("Tenant Suspended");
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) 
    {
        log.error("Unhandled exception", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        pd.setTitle("Internal Server Error");
        return pd;
    }
}
