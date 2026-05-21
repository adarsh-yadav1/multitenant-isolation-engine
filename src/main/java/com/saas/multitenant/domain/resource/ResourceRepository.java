package com.saas.multitenant.domain.resource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceRepository extends JpaRepository<Resource, String> {
    List<Resource> findByOwnerId(String ownerId);
}