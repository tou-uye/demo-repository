package com.aiinvest.api;

import org.springframework.web.bind.annotation.*;
import java.util.*;
import com.aiinvest.repo.MessageRepository;
import com.aiinvest.repo.ReportRepository;
import com.aiinvest.repo.PositionRepository;

@RestController
@RequestMapping("/api/overview")
public class OverviewController {
    private final MessageRepository messageRepository;
    private final ReportRepository reportRepository;
    private final PositionRepository positionRepository;
    public OverviewController(MessageRepository m, ReportRepository r, PositionRepository p) {
        this.messageRepository = m; this.reportRepository = r; this.positionRepository = p;
    }

    @GetMapping
    public Map<String, Object> overview() {
        Map<String, Object> m = new HashMap<>();
        m.put("unreadMessages", messageRepository.countByReadFlagFalse());
        m.put("pendingReports", reportRepository.countByStatus("PENDING"));
        m.put("totalAssetUsd", positionRepository.findAll().stream().map(p -> p.getAmountUsd()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        return m;
    }
}
