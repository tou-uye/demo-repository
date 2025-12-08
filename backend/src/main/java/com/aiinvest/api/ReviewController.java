package com.aiinvest.api;

import com.aiinvest.domain.Position;
import com.aiinvest.domain.Report;
import com.aiinvest.repo.PositionRepository;
import com.aiinvest.repo.ReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.aiinvest.repo.OperationLogRepository;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/review")
public class ReviewController {
    private final ReportRepository repo;
    private final PositionRepository positionRepository;
    private final OperationLogRepository operationLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReviewController(ReportRepository repo, PositionRepository positionRepository, OperationLogRepository operationLogRepository) {
        this.repo = repo;
        this.positionRepository = positionRepository;
        this.operationLogRepository = operationLogRepository;
    }

    @PostMapping("/approve/{id}")
    @Transactional
    public ResponseEntity<?> approve(@PathVariable("id") Long id, @RequestHeader(value = "X-User", required = false) String user) {
        Report r = repo.findById(id).orElse(null);
        if (r == null) {
            return ResponseEntity.notFound().build();
        }
        r.setStatus("APPROVED");
        r.setReviewer(user != null ? user : "admin");
        r.setReviewedAt(OffsetDateTime.now());
        repo.save(r);

        boolean applied = false;
        String failReason = null;
        try {
            applied = applyPlan(r);
            if (!applied) failReason = "plan missing or invalid";
        } catch (Exception e) {
            failReason = e.getMessage();
        }
        if (!applied) {
            log("APPLY_PLAN", "FAILED", "reportId=" + id + (failReason != null ? (", reason=" + failReason) : ""));
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", id);
            resp.put("result", "not_applied");
            resp.put("message", failReason != null ? failReason : "plan not applied");
            return ResponseEntity.status(500).body(resp);
        }
        log("APPLY_PLAN", "SUCCESS", "reportId=" + id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("result", "approved");
        resp.put("planApplied", true);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/reject/{id}")
    public ResponseEntity<?> reject(@PathVariable("id") Long id, @RequestBody Map<String, String> body, @RequestHeader(value = "X-User", required = false) String user) {
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
        if (plan == null || plan.trim().isEmpty()) {
            // fallback: use positionsSnapshotJson if present
            plan = r.getPositionsSnapshotJson();
        }
        if (plan == null || plan.trim().isEmpty()) return false;
        try {
            Map<String, Object> map = objectMapper.readValue(plan, Map.class);
            Object snapshot = map.get("positions_snapshot");
            if (snapshot instanceof List && !((List) snapshot).isEmpty()) {
                List list = (List) snapshot;
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
            // fallback: try adjustments to derive new positions
            Object adjustments = map.get("adjustments");
            if (adjustments instanceof List && snapshot instanceof List && !((List) snapshot).isEmpty()) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void log(String type, String status, String detail) {
        try {
            com.aiinvest.domain.OperationLog log = new com.aiinvest.domain.OperationLog();
            log.setType(type);
            log.setStatus(status);
            log.setDetail(detail);
            log.setCreatedAt(OffsetDateTime.now());
            operationLogRepository.save(log);
        } catch (Exception ignored) {}
    }
}
