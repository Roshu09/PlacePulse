package com.placepulse.notify.repository;

import com.placepulse.notify.domain.KnownTpr;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface KnownTprRepository extends JpaRepository<KnownTpr, UUID> {
}
