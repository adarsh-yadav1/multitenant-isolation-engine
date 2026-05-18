package com.saas.multitenant.domain.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RateLimitEventRepository extends JpaRepository<RateLimitEvent, Long> {
}
