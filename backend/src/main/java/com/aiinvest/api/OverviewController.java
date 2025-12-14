package com.aiinvest.api;

import org.springframework.web.bind.annotation.*;
import java.util.*;
import com.aiinvest.repo.MessageRepository;
import com.aiinvest.repo.ReportRepository;
import com.aiinvest.service.PositionBatchService;

@RestController
@RequestMapping("/api/overview")
public class OverviewController {
    private final MessageRepository messageRepository;
    private final ReportRepository reportRepository;
    private final PositionBatchService positionBatchService;
    public OverviewController(MessageRepository m, ReportRepository r, PositionBatchService positionBatchService) {
        this.messageRepository = m;
        this.reportRepository = r;
        this.positionBatchService = positionBatchService;
    }

    @GetMapping
    public Map<String, Object> overview() {
        Map<String, Object> m = new HashMap<>();
        m.put("unreadMessages", messageRepository.countByReadFlagFalse());
        m.put("pendingReports", reportRepository.countByStatus("PENDING"));
        m.put("totalAssetUsd", positionBatchService.currentPositions().stream().map(p -> p.getAmountUsd()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        return m;
    }
}
