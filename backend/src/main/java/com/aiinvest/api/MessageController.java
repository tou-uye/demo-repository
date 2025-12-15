package com.aiinvest.api;

import com.aiinvest.service.DifyService;
import com.aiinvest.service.CollectJobService;
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
    private final CollectJobService collectJobService;
    private final MessageRepository messageRepository;
    public MessageController(DifyService difyService, CollectJobService collectJobService, MessageRepository messageRepository) {
        this.difyService = difyService;
        this.collectJobService = collectJobService;
        this.messageRepository = messageRepository;
    }

    @PostMapping("/ingest")
    public ResponseEntity<?> ingest(@RequestBody List<Map<String, Object>> payload) {
        if (payload == null || payload.isEmpty()) {
            return ResponseEntity.badRequest().body("payload required");
        }
        int saved = 0;
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> item : payload) {
            Message m = new Message();
            Object title = item.get("title");
            Object symbol = item.get("symbol");
            Object sentiment = item.get("sentiment");
            Object content = item.get("content");
            Object summary = item.get("summary");
            Object impactDescription = item.get("impactDescription");
            Object sourceUrl = item.containsKey("sourceUrl") ? item.get("sourceUrl") : item.get("source_url");

            m.setTitle(title == null ? "" : String.valueOf(title));
            m.setSymbol(symbol == null ? "" : String.valueOf(symbol));
            m.setSentiment(sentiment == null ? "" : String.valueOf(sentiment));
            String source = sourceUrl == null ? "" : String.valueOf(sourceUrl);
            if (source == null || source.trim().isEmpty() || "NONE".equalsIgnoreCase(source.trim())) {
                source = "https://www.binance.com/en/support/announcement";
            }
            m.setSourceUrl(source);
            m.setContent(content == null ? "" : String.valueOf(content));
            m.setSummary(summary == null ? "" : String.valueOf(summary));
            m.setImpactDescription(impactDescription == null ? "" : String.valueOf(impactDescription));
            m.setCreatedAt(java.time.OffsetDateTime.now());
            m.setReadFlag(false);
            messageRepository.save(m);
            saved++;
            ids.add(m.getId());
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("count", saved);
        resp.put("id", ids.isEmpty() ? null : ids.get(0));
        resp.put("ids", ids);
        return ResponseEntity.ok(resp);
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
            row.put("content", m.getContent());
            row.put("summary", m.getSummary());
            row.put("impactDescription", m.getImpactDescription());
            row.put("createdAt", m.getCreatedAt().toString());
            row.put("read", m.isReadFlag());
            out.add(row);
        }
        return out;
    }

    @PostMapping("/collect")
    public ResponseEntity<?> collect() {
        Map<String, Object> resp = collectJobService.trigger("manual");
        return ResponseEntity.accepted().body(resp);
    }

    @GetMapping("/collect/status")
    public Map<String, Object> collectStatus() {
        return collectJobService.status();
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

    @PostMapping("/unread")
    public ResponseEntity<?> markUnread(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) return ResponseEntity.badRequest().body("ids required");
        List<Message> list = messageRepository.findAllById(ids);
        for (Message m : list) {
            m.setReadFlag(false);
        }
        messageRepository.saveAll(list);
        return ResponseEntity.ok(Collections.singletonMap("updated", list.size()));
    }
}
