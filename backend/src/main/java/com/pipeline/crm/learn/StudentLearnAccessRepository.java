package com.pipeline.crm.learn;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentLearnAccessRepository extends JpaRepository<StudentLearnAccess, UUID> {
}
