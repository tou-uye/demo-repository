package com.aiinvest.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.Collections;
import com.aiinvest.domain.Report;
import com.aiinvest.repo.ReportRepository;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportRepository repo;
    public ReportController(ReportRepository repo) { this.repo = repo; }
    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody Map<String, Object> input) {
        Report r = new Report();
        r.setSummary(String.valueOf(input.getOrDefault("summary", "建议报告占位")));
        r.setStatus("PENDING");
        Object mid = input.get("messageId");
        if (mid != null) r.setMessageId(Long.valueOf(String.valueOf(mid)));
        r.setCreatedAt(java.time.OffsetDateTime.now());
        repo.save(r);
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("status", r.getStatus());
        m.put("summary", r.getSummary());
        return m;
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
            out.add(m);
        }
        return out;
    }
}
