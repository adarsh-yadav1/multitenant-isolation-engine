package com.saas.multitenant.domain.resource;

import com.saas.multitenant.dto.ResourceRequest;
import com.saas.multitenant.dto.ResourceResponse;
import com.saas.multitenant.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository resourceRepository;

    // Hardcoded system owner for now — Phase 2 will use JWT subject
    private static final String SYSTEM_OWNER = "00000000-0000-0000-0000-000000000000";

    @Transactional(readOnly = true)
    public List<ResourceResponse> findAll() {
        return resourceRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResourceResponse findById(String id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(id));
        return toResponse(resource);
    }

    @Transactional
    public ResourceResponse create(ResourceRequest req) {
        Resource resource = Resource.builder()
                .name(req.getName())
                .description(req.getData())
                .ownerId(SYSTEM_OWNER)
                .build();
        return toResponse(resourceRepository.save(resource));
    }

    @Transactional
    public ResourceResponse update(String id, ResourceRequest req) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(id));
        resource.setName(req.getName());
        resource.setDescription(req.getData());
        return toResponse(resourceRepository.save(resource));
    }

    @Transactional
    public void delete(String id) {
        if (!resourceRepository.existsById(id)) {
            throw new ResourceNotFoundException(id);
        }
        resourceRepository.deleteById(id);
    }

    private ResourceResponse toResponse(Resource r) {
        return ResourceResponse.builder()
                .id(r.getId())
                .name(r.getName())
                .data(r.getDescription())
                .createdAt(r.getCreatedAt() != null
                        ? r.getCreatedAt().atZone(java.time.ZoneOffset.UTC)
                                .toLocalDateTime()
                        : null)
                .build();
    }
}