package com.aiinvest.repo;

import com.aiinvest.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findAllByOrderByCreatedAtDesc();
    long countBySentiment(String sentiment);
}

