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
            m.setSourceUrl(String.valueOf(item.getOrDefault("sourceUrl", "")));
            m.setCreatedAt(java.time.OffsetDateTime.now());
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
            out.add(row);
        }
        return out;
    }

    @PostMapping("/collect")
    public ResponseEntity<?> collect() {
        difyService.collectAndAnalyze();
        return ResponseEntity.accepted().build();
    }
}
