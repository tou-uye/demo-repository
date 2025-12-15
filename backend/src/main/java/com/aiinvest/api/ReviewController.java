package com.aiinvest.api;

import com.aiinvest.domain.Position;
import com.aiinvest.domain.Report;
import com.aiinvest.repo.PositionRepository;
import com.aiinvest.repo.ReportRepository;
import com.aiinvest.service.PositionBatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.aiinvest.repo.OperationLogRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/review")
public class ReviewController {
    private final ReportRepository repo;
    private final PositionRepository positionRepository;
    private final PositionBatchService positionBatchService;
    private final OperationLogRepository operationLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger applyFailCount = new AtomicInteger(0);
    private static final int APPLY_FAIL_THRESHOLD = 2;

    public ReviewController(ReportRepository repo, PositionRepository positionRepository, PositionBatchService positionBatchService, OperationLogRepository operationLogRepository) {
        this.repo = repo;
        this.positionRepository = positionRepository;
        this.positionBatchService = positionBatchService;
        this.operationLogRepository = operationLogRepository;
    }

    @PostMapping("/approve/{id}")
    @Transactional
    public ResponseEntity<?> approve(@PathVariable("id") Long id, @RequestHeader(value = "X-User", required = false) String user) {
        Report r = repo.findById(id).orElse(null);
        if (r == null) {
            return ResponseEntity.notFound().build();
        }
        boolean applied = false;
        String failReason = null;
        try {
            List<String> errors = new ArrayList<>();
            applied = applyPlan(r, errors);
            if (!applied) {
                failReason = errors.isEmpty() ? "plan missing or invalid" : String.join("; ", errors);
            }
        } catch (Exception e) {
            failReason = e.getMessage();
        }
        if (!applied) {
            int failCnt = applyFailCount.incrementAndGet();
            log("APPLY_PLAN", "FAILED", "reportId=" + id + (failReason != null ? (", reason=" + failReason) : "") + ", failCount=" + failCnt);
            if (failCnt >= APPLY_FAIL_THRESHOLD) {
                log("APPLY_PLAN", "ALERT", "apply failed >= " + APPLY_FAIL_THRESHOLD + ", manual intervention needed");
            }
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", id);
            resp.put("result", "not_applied");
            resp.put("message", failReason != null ? failReason : "plan not applied");
            resp.put("errors", failReason);
            return ResponseEntity.status(500).body(resp);
        }

        r.setStatus("APPROVED");
        r.setReviewer(user != null ? user : "admin");
        r.setReviewedAt(OffsetDateTime.now());
        repo.save(r);

        applyFailCount.set(0);
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

    private boolean applyPlan(Report r, List<String> errors) {
        String plan = r.getPlanJson();
        if (plan == null || plan.trim().isEmpty()) {
            // fallback: use positionsSnapshotJson if present
            plan = r.getPositionsSnapshotJson();
        }
        if (plan == null || plan.trim().isEmpty()) {
            errors.add("plan is empty");
            return false;
        }
        // 尝试按照不同格式解析：planJson -> positionsSnapshotJson -> adjustmentsJson
        if (tryApplyFromJson(plan, errors)) return true;
        if (tryApplyFromJson(r.getPositionsSnapshotJson(), errors)) return true;
        if (tryApplyFromJson(r.getAdjustmentsJson(), errors)) return true;
        errors.add("plan parse error: no valid snapshot or adjustments");
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean tryApplyFromJson(String json, List<String> errors) {
        if (json == null || json.trim().isEmpty()) return false;
        try {
            Object root = objectMapper.readValue(json, Object.class);
            Object snapshot = null;
            Object adjustments = null;
            if (root instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) root;
                snapshot = map.get("positions_snapshot");
                adjustments = map.get("adjustments");
            } else if (root instanceof List) {
                // 直接就是 positions_snapshot 数组
                snapshot = root;
            }
            boolean appliedSnapshot = applySnapshot(snapshot, errors);
            boolean appliedAdjustments = applyAdjustments(adjustments, errors);
            return appliedSnapshot || appliedAdjustments;
        } catch (Exception e) {
            errors.add("plan parse error: " + e.getMessage());
            return false;
        }
    }
    private boolean applySnapshot(Object snapshot, List<String> errors) {
        if (!(snapshot instanceof List) || ((List) snapshot).isEmpty()) return false;
        List list = (List) snapshot;
        OffsetDateTime now = OffsetDateTime.now();
        List<Position> positions = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map)) continue;
            Map row = (Map) o;
            String symbol = String.valueOf(row.getOrDefault("symbol", "")).trim();
            if (symbol.isEmpty()) {
                errors.add("snapshot missing symbol");
                continue;
            }
            BigDecimal percent = toDecimal(row, "percent", "target_percent", "new_percent");
            BigDecimal amount = toDecimal(row, "amountUsd", "amount_usd", "target_amount", "new_amount");
            if (percent == null) {
                percent = BigDecimal.ZERO;
                errors.add("snapshot " + symbol + " missing percent, default 0");
            }
            if (amount == null) {
                amount = BigDecimal.ZERO;
                errors.add("snapshot " + symbol + " missing amount, default 0");
            }
            if (percent.compareTo(BigDecimal.ZERO) < 0 || amount.compareTo(BigDecimal.ZERO) < 0) {
                errors.add("snapshot " + symbol + " negative value skipped");
                continue;
            }
            Position p = new Position();
            p.setSymbol(symbol);
            p.setPercent(percent);
            p.setAmountUsd(amount);
            p.setCreatedAt(now);
            positions.add(p);
        }
        if (positions.isEmpty()) {
            errors.add("snapshot applied 0 rows");
            return false;
        }
        positionRepository.saveAll(positions);
        return true;
    }

    private boolean applyAdjustments(Object adjustments, List<String> errors) {
        if (!(adjustments instanceof List) || ((List) adjustments).isEmpty()) return false;
        Map<String, Position> map = new LinkedHashMap<>();
        OffsetDateTime now = OffsetDateTime.now();
        for (Object o : positionBatchService.currentPositions()) {
            if (o instanceof Position p) {
                map.put(p.getSymbol(), clonePosition(p, now));
            }
        }
        for (Object o : (List) adjustments) {
            if (!(o instanceof Map)) continue;
            Map row = (Map) o;
            String symbol = String.valueOf(row.getOrDefault("symbol", "")).trim();
            if (symbol.isEmpty()) {
                errors.add("adjustment missing symbol");
                continue;
            }
            Position base = map.getOrDefault(symbol, createEmpty(symbol, now));
            BigDecimal targetPercent = toDecimal(row, "percent", "target_percent", "new_percent");
            BigDecimal deltaPercent = toDecimal(row, "delta_percent", "percent_delta");
            BigDecimal targetAmount = toDecimal(row, "amountUsd", "amount_usd", "target_amount", "new_amount");
            BigDecimal deltaAmount = toDecimal(row, "delta_amount", "amount_delta");
            if (deltaPercent != null) base.setPercent(base.getPercent().add(deltaPercent));
            if (targetPercent != null) base.setPercent(targetPercent);
            if (deltaAmount != null) base.setAmountUsd(base.getAmountUsd().add(deltaAmount));
            if (targetAmount != null) base.setAmountUsd(targetAmount);
            if (base.getPercent() != null && base.getPercent().compareTo(BigDecimal.ZERO) < 0) {
                errors.add("adjustment " + symbol + " negative percent skipped");
                continue;
            }
            if (base.getAmountUsd() != null && base.getAmountUsd().compareTo(BigDecimal.ZERO) < 0) {
                errors.add("adjustment " + symbol + " negative amount skipped");
                continue;
            }
            map.put(symbol, base);
        }
        if (map.isEmpty()) {
            errors.add("adjustments applied 0 rows");
            return false;
        }
        BigDecimal totalPercent = map.values().stream().map(Position::getPercent).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalPercent.compareTo(new BigDecimal("200")) > 0 || totalPercent.compareTo(new BigDecimal("50")) < 0) {
            errors.add("percent total out of range: " + totalPercent);
            return false;
        }
        positionRepository.saveAll(map.values());
        return true;
    }

    private Position clonePosition(Position p, OffsetDateTime now) {
        Position n = new Position();
        n.setSymbol(p.getSymbol());
        n.setPercent(p.getPercent() != null ? p.getPercent() : BigDecimal.ZERO);
        n.setAmountUsd(p.getAmountUsd() != null ? p.getAmountUsd() : BigDecimal.ZERO);
        n.setCreatedAt(now);
        return n;
    }

    private Position createEmpty(String symbol, OffsetDateTime now) {
        Position p = new Position();
        p.setSymbol(symbol);
        p.setPercent(BigDecimal.ZERO);
        p.setAmountUsd(BigDecimal.ZERO);
        p.setCreatedAt(now);
        return p;
    }

    private BigDecimal toDecimal(Map row, String... keys) {
        for (String k : keys) {
            Object v = row.get(k);
            if (v == null) continue;
            try {
                if (v instanceof Number) {
                    return new BigDecimal(String.valueOf(((Number) v).doubleValue()));
                }
                String s = String.valueOf(v).trim();
                if (!s.isEmpty()) return new BigDecimal(s);
            } catch (Exception ignored) {}
        }
        return null;
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
