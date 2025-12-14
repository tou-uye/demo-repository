package com.aiinvest.repo;

import com.aiinvest.domain.Message;
import com.aiinvest.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findAllByOrderByCreatedAtDesc();

    @Query("select m from Message m where not exists (select 1 from Report r where r.messageId = m.id) order by m.createdAt desc")
    List<Message> findUnreported(Pageable pageable);

    long countBySentiment(String sentiment);
    boolean existsByTitle(String title);
    boolean existsBySourceUrl(String sourceUrl);
    long countByReadFlagFalse();
}
