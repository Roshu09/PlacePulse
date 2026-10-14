package com.placepulse.notify.repository;

import com.placepulse.notify.domain.KnownStudent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface KnownStudentRepository extends JpaRepository<KnownStudent, UUID> {
}
