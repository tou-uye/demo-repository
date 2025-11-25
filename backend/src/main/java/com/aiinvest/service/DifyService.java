package com.aiinvest.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import com.aiinvest.domain.Message;
import com.aiinvest.domain.Report;
import com.aiinvest.repo.MessageRepository;
import com.aiinvest.repo.ReportRepository;

@Service
public class DifyService {
    @Value("${dify.apiKey:}")
    private String apiKey;
    @Value("${dify.baseUrl:https://api.dify.ai/v1}")
    private String baseUrl;
    private final RestTemplate restTemplate;
    private final MessageRepository messageRepository;
    private final ReportRepository reportRepository;
    @Value("${retry.maxAttempts:3}")
    private int maxAttempts;
    @Value("${retry.delayMillis:2000}")
    private long delayMillis;

    public DifyService(RestTemplate restTemplate, MessageRepository messageRepository, ReportRepository reportRepository) {
        this.restTemplate = restTemplate;
        this.messageRepository = messageRepository;
        this.reportRepository = reportRepository;
    }

    public void collectAndAnalyze() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return;
        }
        List<Map<String, Object>> trending = fetchTrending();
        for (Map<String, Object> t : trending) {
            Message m = new Message();
            m.setTitle(String.valueOf(t.getOrDefault("title", "")));
            m.setSymbol(String.valueOf(t.getOrDefault("symbol", "")));
            m.setSentiment("中性");
            m.setSourceUrl(String.valueOf(t.getOrDefault("sourceUrl", "")));
            m.setCreatedAt(java.time.OffsetDateTime.now());
            messageRepository.save(m);

            String summary = callDifySummary(m);
            Report r = new Report();
            r.setSummary(summary);
            r.setStatus("PENDING");
            r.setMessageId(m.getId());
            r.setCreatedAt(java.time.OffsetDateTime.now());
            reportRepository.save(r);
        }
    }

    private List<Map<String, Object>> fetchTrending() {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity("https://api.coingecko.com/api/v3/search/trending", Map.class);
            Object coins = ((Map)resp.getBody()).get("coins");
            if (coins instanceof List) {
                for (Object c : (List) coins) {
                    Object item = ((Map) c).get("item");
                    if (item instanceof Map) {
                        String symbol = String.valueOf(((Map)item).getOrDefault("symbol", ""));
                        String name = String.valueOf(((Map)item).getOrDefault("name", ""));
                        Map<String, Object> row = new java.util.HashMap<>();
                        row.put("symbol", symbol);
                        row.put("title", "Trending: " + name + " (" + symbol + ")");
                        row.put("sourceUrl", "https://www.coingecko.com");
                        out.add(row);
                    }
                }
            }
        } catch (Exception ignored) {}
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity("https://data-api.binance.vision/api/v3/ticker/price?symbol=BTCUSDT", Map.class);
            Object price = resp.getBody() != null ? resp.getBody().get("price") : null;
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("symbol", "BTC");
            row.put("title", "Binance BTCUSDT price: " + String.valueOf(price));
            row.put("sourceUrl", "https://www.binance.com");
            out.add(row);
        } catch (Exception ignored) {}
        return out;
    }

    private String callDifySummary(Message m) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        Map<String, Object> inputs = new java.util.HashMap<>();
        inputs.put("title", m.getTitle());
        inputs.put("symbol", m.getSymbol());
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("inputs", inputs);
        payload.put("response_mode", "blocking");
        payload.put("user", "system-cron");
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        int attempts = 0;
        while (attempts < maxAttempts) {
            attempts++;
            try {
                ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + "/completion-messages", entity, Map.class);
                Object output = resp.getBody() != null ? resp.getBody().get("data") : null;
                if (output != null) {
                    return String.valueOf(output);
                }
            } catch (Exception e) {
                try { Thread.sleep(delayMillis); } catch (InterruptedException ignored) {}
            }
        }
        return m.getTitle();
    }
}
