package com.aiinvest.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.aiinvest.repo.PositionRepository;
import com.aiinvest.repo.PositionSnapshotRepository;
import com.aiinvest.domain.PositionSnapshot;

@Service
public class ScheduleService {
    private final DifyService difyService;
    private final PositionRepository positionRepository;
    private final PositionSnapshotRepository positionSnapshotRepository;
    public ScheduleService(DifyService difyService, PositionRepository positionRepository, PositionSnapshotRepository positionSnapshotRepository) {
        this.difyService = difyService;
        this.positionRepository = positionRepository;
        this.positionSnapshotRepository = positionSnapshotRepository;
    }

    @Scheduled(cron = "0 5 8 * * ?")
    public void snapshotPositions() {
        java.time.LocalDate today = java.time.LocalDate.now();
        positionSnapshotRepository.deleteAll(positionSnapshotRepository.findBySnapshotDateBetweenOrderBySnapshotDate(today, today));
        positionRepository.findAll().forEach(p -> {
            PositionSnapshot s = new PositionSnapshot();
            s.setSnapshotDate(today);
            s.setSymbol(p.getSymbol());
            s.setTotalUsd(p.getAmountUsd());
            positionSnapshotRepository.save(s);
        });
    }
}
