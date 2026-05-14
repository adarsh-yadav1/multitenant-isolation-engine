package com.saas.multitenant.domain.tenant;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, String> {

    Optional<Tenant> findByTenantId(String tenantId);
}
