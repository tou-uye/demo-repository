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

    @GetMapping("/ai")
    public List<Position> aiCurrent() {
        return repo.findByCreatedAtIsNotNullOrderByCreatedAtDesc();
    }

    @GetMapping("/history")
    public List<Position> history() {
        return repo.findAll();
    }

    @PostMapping("/update")
    @Transactional
    public ResponseEntity<?> update(@RequestBody @Valid List<UpdatePositionRequest> items) {
        if (items == null || items.isEmpty()) {
            return ResponseEntity.badRequest().body("positions required");
        }
        java.math.BigDecimal percentTotal = java.math.BigDecimal.ZERO;
        java.math.BigDecimal amountTotal = java.math.BigDecimal.ZERO;
        repo.deleteAll();
        OffsetDateTime now = OffsetDateTime.now();
        for (UpdatePositionRequest r : items) {
            if (r.getSymbol() == null || r.getSymbol().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("symbol cannot be empty");
            }
            Position p = new Position();
            p.setSymbol(r.getSymbol());
            p.setPercent(r.getPercent());
            p.setAmountUsd(r.getAmountUsd());
            p.setCreatedAt(now);
            repo.save(p);
            percentTotal = percentTotal.add(r.getPercent());
            amountTotal = amountTotal.add(r.getAmountUsd());
        }
        if (percentTotal.compareTo(new java.math.BigDecimal("99")) < 0 || percentTotal.compareTo(new java.math.BigDecimal("101")) > 0) {
            return ResponseEntity.badRequest().body("total percent must be close to 100 (99-101)");
        }
        if (amountTotal.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body("total amount must be positive");
        }
        return ResponseEntity.ok().build();
    }
}
