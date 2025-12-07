package com.aiinvest.repo;

import com.aiinvest.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
    long countByMessageId(Long messageId);
}
