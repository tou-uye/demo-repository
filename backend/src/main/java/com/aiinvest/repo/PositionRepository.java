package com.aiinvest.repo;

import com.aiinvest.domain.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.time.OffsetDateTime;

public interface PositionRepository extends JpaRepository<Position, Long> {
    Position findTopByOrderByCreatedAtDesc();
    List<Position> findByCreatedAtOrderBySymbolAsc(OffsetDateTime createdAt);
    List<Position> findAllByOrderByCreatedAtDesc();
}
