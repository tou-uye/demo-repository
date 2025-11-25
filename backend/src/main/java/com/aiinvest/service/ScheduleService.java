package com.aiinvest.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ScheduleService {
    private final DifyService difyService;
    public ScheduleService(DifyService difyService) { this.difyService = difyService; }

    @Scheduled(cron = "0 0 8 * * ?")
    public void daily() {
        difyService.collectAndAnalyze();
    }
}

