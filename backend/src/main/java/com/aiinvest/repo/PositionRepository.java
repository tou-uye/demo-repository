package com.aiinvest.repo;

import com.aiinvest.domain.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PositionRepository extends JpaRepository<Position, Long> {
    List<Position> findByCreatedAtIsNotNullOrderByCreatedAtDesc();
}

