package com.aiinvest.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;
import com.aiinvest.repo.ReportRepository;
import com.aiinvest.domain.Report;

@RestController
@RequestMapping("/api/review")
public class ReviewController {
    private final ReportRepository repo;
    public ReviewController(ReportRepository repo) { this.repo = repo; }
    @PostMapping("/approve/{id}")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        Report r = repo.findById(id).orElse(null);
        if (r != null) { r.setStatus("APPROVED"); repo.save(r); }
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("result", "approved");
        return ResponseEntity.ok(m);
    }

    @PostMapping("/reject/{id}")
    public ResponseEntity<?> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Report r = repo.findById(id).orElse(null);
        if (r != null) { r.setStatus("REJECTED"); repo.save(r); }
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("result", "rejected");
        m.put("reason", body.getOrDefault("reason", ""));
        return ResponseEntity.ok(m);
    }
}
