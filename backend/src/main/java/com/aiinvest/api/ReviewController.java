package com.aiinvest.api;

import com.aiinvest.domain.Position;
import com.aiinvest.domain.Report;
import com.aiinvest.repo.PositionRepository;
import com.aiinvest.repo.ReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/review")
public class ReviewController {
    private final ReportRepository repo;
    private final PositionRepository positionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReviewController(ReportRepository repo, PositionRepository positionRepository) {
        this.repo = repo;
        this.positionRepository = positionRepository;
    }

    @PostMapping("/approve/{id}")
    @Transactional
    public ResponseEntity<?> approve(@PathVariable Long id, @RequestHeader(value = "X-User", required = false) String user) {
        Report r = repo.findById(id).orElse(null);
        if (r != null) {
            r.setStatus("APPROVED");
            r.setReviewer(user != null ? user : "admin");
            r.setReviewedAt(OffsetDateTime.now());
            repo.save(r);
            boolean applied = applyPlan(r);
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", id);
            resp.put("result", "approved");
            resp.put("planApplied", applied);
            return ResponseEntity.ok(resp);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("result", "approved");
        return ResponseEntity.ok(m);
    }

    @PostMapping("/reject/{id}")
    public ResponseEntity<?> reject(@PathVariable Long id, @RequestBody Map<String, String> body, @RequestHeader(value = "X-User", required = false) String user) {
        Report r = repo.findById(id).orElse(null);
        if (r != null) {
            r.setStatus("REJECTED");
            r.setReviewReason(body.getOrDefault("reason", ""));
            r.setReviewer(user != null ? user : "admin");
            r.setReviewedAt(OffsetDateTime.now());
            repo.save(r);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("result", "rejected");
        m.put("reason", body.getOrDefault("reason", ""));
        return ResponseEntity.ok(m);
    }

    private boolean applyPlan(Report r) {
        String plan = r.getPlanJson();
        if (plan == null || plan.trim().isEmpty()) return false;
        try {
            Map<String, Object> map = objectMapper.readValue(plan, Map.class);
            Object snapshot = map.get("positions_snapshot");
            if (snapshot instanceof List) {
                List list = (List) snapshot;
                if (!list.isEmpty()) {
                    positionRepository.deleteAll();
                    OffsetDateTime now = OffsetDateTime.now();
                    for (Object o : list) {
                        if (o instanceof Map) {
                            Map row = (Map) o;
                            String symbol = String.valueOf(row.getOrDefault("symbol", "")).trim();
                            if (symbol.isEmpty()) continue;
                            java.math.BigDecimal percent = new java.math.BigDecimal(String.valueOf(row.getOrDefault("percent", "0")));
                            java.math.BigDecimal amount = new java.math.BigDecimal(String.valueOf(row.getOrDefault("amountUsd", "0")));
                            Position p = new Position();
                            p.setSymbol(symbol);
                            p.setPercent(percent);
                            p.setAmountUsd(amount);
                            p.setCreatedAt(now);
                            positionRepository.save(p);
                        }
                    }
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
}
