package com.aiinvest.api;

import com.aiinvest.domain.Position;
import com.aiinvest.repo.PositionRepository;
import com.aiinvest.api.dto.UpdatePositionRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/positions")
public class PositionController {
    private final PositionRepository repo;
    public PositionController(PositionRepository repo) { this.repo = repo; }

    @GetMapping("/current")
    public List<Position> current() {
        return repo.findByCreatedAtIsNotNullOrderByCreatedAtDesc();
    }

    @GetMapping("/history")
    public List<Position> history() {
        return repo.findAll();
    }

    @PostMapping("/update")
    @Transactional
    public ResponseEntity<?> update(@RequestBody @Valid List<UpdatePositionRequest> items) {
        repo.deleteAll();
        OffsetDateTime now = OffsetDateTime.now();
        for (UpdatePositionRequest r : items) {
            Position p = new Position();
            p.setSymbol(r.getSymbol());
            p.setPercent(r.getPercent());
            p.setAmountUsd(r.getAmountUsd());
            p.setCreatedAt(now);
            repo.save(p);
        }
        return ResponseEntity.ok().build();
    }
}

