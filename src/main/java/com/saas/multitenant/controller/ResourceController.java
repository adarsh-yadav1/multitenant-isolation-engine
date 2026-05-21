package com.saas.multitenant.controller;

import com.saas.multitenant.domain.resource.ResourceService;
import com.saas.multitenant.dto.ResourceRequest;
import com.saas.multitenant.dto.ResourceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
@Tag(name = "Resources", description = "Tenant-scoped resource CRUD")
public class ResourceController {

    private final ResourceService resourceService;

    @GetMapping
    @Operation(summary = "List all resources for this tenant")
    public List<ResourceResponse> list() {
        return resourceService.findAll();
    }

    @PostMapping
    @Operation(summary = "Create a new resource")
    public ResponseEntity<ResourceResponse> create(
            @Valid @RequestBody ResourceRequest req) {
        return ResponseEntity.status(201).body(resourceService.create(req));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a resource by ID")
    public ResourceResponse get(@PathVariable String id) {
        return resourceService.findById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a resource")
    public ResourceResponse update(@PathVariable String id,
            @Valid @RequestBody ResourceRequest req) {
        return resourceService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a resource")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        resourceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}