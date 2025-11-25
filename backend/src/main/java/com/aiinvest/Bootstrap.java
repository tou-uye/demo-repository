package com.aiinvest;

import com.aiinvest.domain.Position;
import com.aiinvest.repo.PositionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Component
public class Bootstrap implements ApplicationRunner {
    private final PositionRepository repo;
    public Bootstrap(PositionRepository repo) { this.repo = repo; }
    @Override
    public void run(ApplicationArguments args) {
        if (repo.count() == 0) {
            OffsetDateTime now = OffsetDateTime.now();
            Position p1 = new Position(); p1.setSymbol("BTC"); p1.setPercent(new BigDecimal("40")); p1.setAmountUsd(new BigDecimal("4000000")); p1.setCreatedAt(now); repo.save(p1);
            Position p2 = new Position(); p2.setSymbol("ETH"); p2.setPercent(new BigDecimal("35")); p2.setAmountUsd(new BigDecimal("3500000")); p2.setCreatedAt(now); repo.save(p2);
            Position p3 = new Position(); p3.setSymbol("SOL"); p3.setPercent(new BigDecimal("15")); p3.setAmountUsd(new BigDecimal("1500000")); p3.setCreatedAt(now); repo.save(p3);
            Position p4 = new Position(); p4.setSymbol("USDT"); p4.setPercent(new BigDecimal("10")); p4.setAmountUsd(new BigDecimal("1000000")); p4.setCreatedAt(now); repo.save(p4);
        }
    }
}

