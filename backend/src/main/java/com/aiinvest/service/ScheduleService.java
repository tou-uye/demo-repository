package com.aiinvest.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.aiinvest.repo.PositionRepository;
import com.aiinvest.repo.PositionSnapshotRepository;
import com.aiinvest.domain.PositionSnapshot;
import com.aiinvest.service.PositionBatchService;

@Service
public class ScheduleService {
    private final DifyService difyService;
    private final PositionSnapshotRepository positionSnapshotRepository;
    private final PositionBatchService positionBatchService;
    public ScheduleService(DifyService difyService, PositionRepository positionRepository, PositionSnapshotRepository positionSnapshotRepository, PositionBatchService positionBatchService) {
        this.difyService = difyService;
        this.positionSnapshotRepository = positionSnapshotRepository;
        this.positionBatchService = positionBatchService;
    }

    // 默认每天 08:05 生成快照，可通过 snapshot.cron 覆盖
    @Scheduled(cron = "${snapshot.cron:0 5 8 * * ?}")
    public void snapshotPositions() {
        java.time.LocalDate today = java.time.LocalDate.now();
        positionSnapshotRepository.deleteAll(positionSnapshotRepository.findBySnapshotDateBetweenOrderBySnapshotDate(today, today));
        positionBatchService.currentPositions().forEach(p -> {
            PositionSnapshot s = new PositionSnapshot();
            s.setSnapshotDate(today);
            s.setSymbol(p.getSymbol());
            s.setTotalUsd(p.getAmountUsd());
            positionSnapshotRepository.save(s);
        });
    }
}
