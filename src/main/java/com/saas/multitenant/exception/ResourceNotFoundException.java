package com.saas.multitenant.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String id) {
        super("Resource not found: " + id);
    }
}