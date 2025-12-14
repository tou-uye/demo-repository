package com.aiinvest.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.Collections;
import com.aiinvest.domain.Report;
import com.aiinvest.repo.ReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportRepository repo;
    private final ObjectMapper objectMapper = new ObjectMapper();
    public ReportController(ReportRepository repo) { this.repo = repo; }
    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody Map<String, Object> input) {
        Report r = new Report();
        r.setSummary(trimSummary(String.valueOf(input.getOrDefault("summary", "建议报告占位"))));
        r.setStatus("PENDING");
        Object mid = input.get("messageId");
        if (mid != null) r.setMessageId(Long.valueOf(String.valueOf(mid)));
        Object plan = input.get("plan");
        Object analysis = input.get("analysis");
        if (plan != null) {
            try { r.setPlanJson(objectMapper.writeValueAsString(plan)); } catch (Exception ignored) {}
            if (plan instanceof Map) {
                Object ps = ((Map) plan).get("positions_snapshot");
                Object adj = ((Map) plan).get("adjustments");
                Object risk = ((Map) plan).get("risk_notes");
                Object conf = ((Map) plan).get("confidence");
                Object senti = ((Map) plan).get("sentiment");
                Object impact = ((Map) plan).get("impact_strength");
                Object kp = ((Map) plan).get("key_points");
                if (ps != null) try { r.setPositionsSnapshotJson(objectMapper.writeValueAsString(ps)); } catch (Exception ignored) {}
                if (adj != null) try { r.setAdjustmentsJson(objectMapper.writeValueAsString(adj)); } catch (Exception ignored) {}
                if (risk != null) r.setRiskNotes(String.valueOf(risk));
                if (conf != null) r.setConfidence(String.valueOf(conf));
                if (senti != null) r.setSentiment(String.valueOf(senti));
                if (impact != null) r.setImpactStrength(String.valueOf(impact));
                if (kp != null) r.setKeyPoints(String.valueOf(kp));
            }
        }
        if (analysis != null) {
            try { r.setAnalysisJson(objectMapper.writeValueAsString(analysis)); } catch (Exception ignored) {}
            if (r.getSummary() == null || r.getSummary().isBlank()) {
                r.setSummary(trimSummary(String.valueOf(analysis)));
            }
        }
        r.setCreatedAt(java.time.OffsetDateTime.now());
        repo.save(r);
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("status", r.getStatus());
        m.put("summary", r.getSummary());
        return m;
    }

    private String trimSummary(String s) {
        if (s == null) return "";
        String v = s.trim();
        int limit = 250;
        return v.length() > limit ? v.substring(0, limit) : v;
    }

    @GetMapping("/pending")
    public List<Map<String, Object>> pending() {
        List<Report> list = repo.findByStatusOrderByCreatedAtDesc("PENDING");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Report r : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("summary", r.getSummary());
            m.put("status", r.getStatus());
            m.put("messageId", r.getMessageId());
            m.put("createdAt", r.getCreatedAt().toString());
            m.put("planJson", r.getPlanJson());
            m.put("analysisJson", r.getAnalysisJson());
            m.put("positionsSnapshotJson", r.getPositionsSnapshotJson());
            m.put("adjustmentsJson", r.getAdjustmentsJson());
            m.put("riskNotes", r.getRiskNotes());
            m.put("confidence", r.getConfidence());
            m.put("sentiment", r.getSentiment());
            m.put("impactStrength", r.getImpactStrength());
            m.put("keyPoints", r.getKeyPoints());
            out.add(m);
        }
        return out;
    }

    @GetMapping
    public List<Map<String, Object>> listAll(@RequestParam(value = "status", required = false) String status) {
        List<Report> list;
        if (status == null || status.equalsIgnoreCase("ALL")) {
            list = repo.findAll();
        } else {
            list = repo.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Report r : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("summary", r.getSummary());
            m.put("status", r.getStatus());
            m.put("messageId", r.getMessageId());
            m.put("createdAt", r.getCreatedAt().toString());
            m.put("reviewedAt", r.getReviewedAt());
            m.put("reviewer", r.getReviewer());
            m.put("reviewReason", r.getReviewReason());
            m.put("planJson", r.getPlanJson());
            m.put("analysisJson", r.getAnalysisJson());
            m.put("positionsSnapshotJson", r.getPositionsSnapshotJson());
            m.put("adjustmentsJson", r.getAdjustmentsJson());
            m.put("riskNotes", r.getRiskNotes());
            m.put("confidence", r.getConfidence());
            m.put("sentiment", r.getSentiment());
            m.put("impactStrength", r.getImpactStrength());
            m.put("keyPoints", r.getKeyPoints());
            out.add(m);
        }
        return out;
    }
}
