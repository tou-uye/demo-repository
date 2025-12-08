package com.aiinvest.api;

import com.aiinvest.service.DifyService;
import com.aiinvest.repo.MessageRepository;
import com.aiinvest.domain.Message;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.Collections;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final DifyService difyService;
    private final MessageRepository messageRepository;
    public MessageController(DifyService difyService, MessageRepository messageRepository) { this.difyService = difyService; this.messageRepository = messageRepository; }

    @PostMapping("/ingest")
    public ResponseEntity<?> ingest(@RequestBody List<Map<String, Object>> payload) {
        int saved = 0;
        for (Map<String, Object> item : payload) {
            Message m = new Message();
            m.setTitle(String.valueOf(item.getOrDefault("title", "")));
            m.setSymbol(String.valueOf(item.getOrDefault("symbol", "")));
            m.setSentiment(String.valueOf(item.getOrDefault("sentiment", "")));
            String source = String.valueOf(item.getOrDefault("sourceUrl", ""));
            if (source == null || source.trim().isEmpty() || "NONE".equalsIgnoreCase(source.trim())) {
                source = "https://www.binance.com/en/support/announcement";
            }
            m.setSourceUrl(source);
            m.setCreatedAt(java.time.OffsetDateTime.now());
            m.setReadFlag(false);
            messageRepository.save(m);
            saved++;
        }
        return ResponseEntity.ok(Collections.singletonMap("count", saved));
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        List<Message> list = messageRepository.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Message m : list) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", m.getId());
            row.put("title", m.getTitle());
            row.put("symbol", m.getSymbol());
            row.put("sentiment", m.getSentiment());
            row.put("sourceUrl", m.getSourceUrl());
            row.put("createdAt", m.getCreatedAt().toString());
            row.put("read", m.isReadFlag());
            out.add(row);
        }
        return out;
    }

    @PostMapping("/collect")
    public ResponseEntity<?> collect() {
        difyService.collectAndAnalyze();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/read")
    public ResponseEntity<?> markRead(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) return ResponseEntity.badRequest().body("ids required");
        List<Message> list = messageRepository.findAllById(ids);
        for (Message m : list) {
            m.setReadFlag(true);
        }
        messageRepository.saveAll(list);
        return ResponseEntity.ok(Collections.singletonMap("updated", list.size()));
    }
}
