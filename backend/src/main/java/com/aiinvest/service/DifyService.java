package com.aiinvest.service;

import com.aiinvest.domain.Message;
import com.aiinvest.domain.Report;
import com.aiinvest.repo.MessageRepository;
import com.aiinvest.repo.OperationLogRepository;
import com.aiinvest.repo.PositionRepository;
import com.aiinvest.repo.ReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Collections;

@Service
public class DifyService {
    @Value("${dify.apiKey:}")
    private String apiKey;
    @Value("${dify.workflowId:}")
    private String workflowId;
    @Value("${dify.baseUrl:https://api.dify.ai/v1}")
    private String baseUrl;
    private final RestTemplate restTemplate;
    private final MessageRepository messageRepository;
    private final OperationLogRepository operationLogRepository;
    private final ReportRepository reportRepository;
    private final PositionRepository positionRepository;
    @Value("${retry.maxAttempts:3}")
    private int maxAttempts;
    @Value("${retry.delayMillis:2000}")
    private long delayMillis;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final java.util.concurrent.atomic.AtomicInteger invalidCount = new java.util.concurrent.atomic.AtomicInteger(0);

    public DifyService(RestTemplate restTemplate, MessageRepository messageRepository, OperationLogRepository operationLogRepository, ReportRepository reportRepository, PositionRepository positionRepository) {
        this.restTemplate = restTemplate;
        this.messageRepository = messageRepository;
        this.operationLogRepository = operationLogRepository;
        this.reportRepository = reportRepository;
        this.positionRepository = positionRepository;
    }

    @PostConstruct
    public void validateConfig() {
        if (isBlank(apiKey) || isBlank(workflowId)) {
            throw new IllegalStateException("Dify API_KEY or WORKFLOW_ID is missing; please set DIFY_API_KEY and DIFY_WORKFLOW_ID.");
        }
    }

    public void collectAndAnalyze() {
        if (isBlank(apiKey) || isBlank(workflowId)) {
            return;
        }
        // 自动采集外部源
        collectExternalSources();

        int processed = 0;
        List<Message> messages = messageRepository.findAllByOrderByCreatedAtDesc();
        for (Message m : messages) {
            if (m.getId() == null) continue;
            if (reportRepository.countByMessageId(m.getId()) > 0) continue;
            WorkflowResult result = callDifyWorkflow(m);
            if (result.getSentiment() != null) {
                m.setSentiment(result.getSentiment());
                messageRepository.save(m);
            }
            if (!isValidResult(result)) {
                log("WORKFLOW_OUTPUT", "INVALID", "messageId=" + m.getId());
                continue;
            }
            Report r = new Report();
            r.setSummary(result.getSummary());
            r.setPlanJson(result.getPlanJson());
            r.setAnalysisJson(result.getAnalysisJson());
            r.setPositionsSnapshotJson(result.getPositionsSnapshotJson());
            r.setAdjustmentsJson(result.getAdjustmentsJson());
            r.setRiskNotes(result.getRiskNotes());
            r.setConfidence(result.getConfidence());
            r.setImpactStrength(result.getImpactStrength());
            r.setKeyPoints(result.getKeyPoints());
            r.setSentiment(result.getSentiment());
            r.setStatus("PENDING");
            r.setMessageId(m.getId());
            r.setCreatedAt(java.time.OffsetDateTime.now());
            reportRepository.save(r);
            processed++;
        }
        logCollect(processed);
    }

    private WorkflowResult callDifyWorkflow(Message m) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> inputs = new java.util.HashMap<>();
        inputs.put("title", m.getTitle());
        inputs.put("symbol", m.getSymbol());
        try {
            inputs.put("positions", positionRepository.findAll());
        } catch (Exception ignored) {}
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("inputs", inputs);
        payload.put("response_mode", "blocking");
        payload.put("user", "system-cron");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        int attempts = 0;
        while (attempts < maxAttempts) {
            attempts++;
            try {
                ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + "/workflows/" + workflowId + "/run", entity, Map.class);
                Map body = resp.getBody();
                if (body != null) {
                    Object data = body.get("data");
                    Object outputs = data instanceof Map ? ((Map) data).get("outputs") : null;
                    WorkflowResult result = parseOutputs(outputs);
                    if (result.getSummary() != null || result.getPlanJson() != null || result.getSentiment() != null) {
                        return result;
                    }
                    Object message = body.get("message");
                    if (message != null) {
                        result.setSummary(stringify(message));
                        return result;
                    }
                }
            } catch (Exception e) {
                try { Thread.sleep(delayMillis); } catch (InterruptedException ignored) {}
            }
        }
        WorkflowResult fallback = new WorkflowResult();
        fallback.setSummary(m.getTitle());
        return fallback;
    }

    private String stringify(Object obj) {
        if (obj == null) return "";
        if (obj instanceof List) {
            StringBuilder sb = new StringBuilder();
            for (Object o : (List) obj) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(String.valueOf(o));
            }
            return sb.toString();
        }
        return String.valueOf(obj);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private WorkflowResult parseOutputs(Object outputs) {
        WorkflowResult result = new WorkflowResult();
        if (outputs == null) return result;
        try {
            if (outputs instanceof Map) {
                Map outMap = (Map) outputs;
                Object plan = outMap.get("plan");
                Object analysis = outMap.get("analysis");
                Object sentiment = outMap.get("sentiment");
                Object positionsSnapshot = outMap.get("positions_snapshot");
                Object adjustments = outMap.get("adjustments");
                Object riskNotes = outMap.get("risk_notes");
                Object confidence = outMap.get("confidence");
                Object impactStrength = outMap.get("impact_strength");
                Object keyPoints = outMap.get("key_points");
                if (analysis instanceof Map && sentiment == null) {
                    Object s = ((Map) analysis).get("sentiment");
                    if (s != null) sentiment = s;
                }
                if (sentiment != null) result.setSentiment(String.valueOf(sentiment));
                if (impactStrength != null) result.setImpactStrength(String.valueOf(impactStrength));
                if (keyPoints != null) result.setKeyPoints(stringify(keyPoints));
                if (positionsSnapshot != null) result.setPositionsSnapshotJson(toJson(positionsSnapshot));
                if (adjustments != null) result.setAdjustmentsJson(toJson(adjustments));
                if (riskNotes != null) result.setRiskNotes(String.valueOf(riskNotes));
                if (confidence != null) result.setConfidence(String.valueOf(confidence));
                if (plan != null) {
                    result.setPlanJson(toJson(plan));
                    result.setSummary(stringify(plan));
                    return result;
                }
                if (analysis != null) {
                    result.setAnalysisJson(toJson(analysis));
                    result.setSummary(stringify(analysis));
                    return result;
                }
                result.setSummary(stringify(outputs));
                return result;
            }
            if (outputs instanceof List) {
                result.setSummary(stringify(outputs));
                result.setPlanJson(toJson(outputs));
                return result;
            }
            result.setSummary(String.valueOf(outputs));
        } catch (Exception ignored) {}
        return result;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return stringify(obj);
        }
    }

    private void logCollect(int processed) {
        try {
            com.aiinvest.domain.OperationLog log = new com.aiinvest.domain.OperationLog();
            log.setType("COLLECT");
            log.setStatus(processed > 0 ? "SUCCESS" : "EMPTY");
            log.setDetail(processed > 0 ? "processed=" + processed : "no new messages to process");
            log.setCreatedAt(java.time.OffsetDateTime.now());
            operationLogRepository.save(log);
        } catch (Exception ignored) {}
    }

    private void log(String type, String status, String detail) {
        try {
            com.aiinvest.domain.OperationLog log = new com.aiinvest.domain.OperationLog();
            log.setType(type);
            log.setStatus(status);
            log.setDetail(detail);
            log.setCreatedAt(java.time.OffsetDateTime.now());
            operationLogRepository.save(log);
        } catch (Exception ignored) {}
    }

    private void collectExternalSources() {
        int saved = 0;
        try {
            // CoinGecko trending
            ResponseEntity<Map> resp = restTemplate.getForEntity("https://api.coingecko.com/api/v3/search/trending", Map.class);
            Object coins = resp.getBody() != null ? ((Map) resp.getBody()).get("coins") : null;
            if (coins instanceof List) {
                for (Object c : (List) coins) {
                    Object item = ((Map) c).get("item");
                    if (item instanceof Map) {
                        String symbol = String.valueOf(((Map) item).getOrDefault("symbol", ""));
                        String name = String.valueOf(((Map) item).getOrDefault("name", ""));
                        String title = "Trending: " + name + " (" + symbol + ")";
                        if (!messageRepository.existsByTitle(title)) {
                            Message m = new Message();
                            m.setTitle(title);
                            m.setSymbol(symbol);
                            m.setSentiment("中性");
                            m.setSourceUrl("https://www.coingecko.com");
                            m.setCreatedAt(java.time.OffsetDateTime.now());
                            m.setReadFlag(false);
                            messageRepository.save(m);
                            saved++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log("FETCH", "ERROR", "coingecko: " + e.getMessage());
        }
        try {
            // Binance price
            ResponseEntity<Map> resp = restTemplate.getForEntity("https://data-api.binance.vision/api/v3/ticker/price?symbol=BTCUSDT", Map.class);
            Object price = resp.getBody() != null ? resp.getBody().get("price") : null;
            String title = "Binance BTCUSDT price: " + String.valueOf(price);
            if (!messageRepository.existsByTitle(title)) {
                Message m = new Message();
                m.setTitle(title);
                m.setSymbol("BTC");
                m.setSentiment("中性");
                m.setSourceUrl("https://www.binance.com");
                m.setCreatedAt(java.time.OffsetDateTime.now());
                m.setReadFlag(false);
                messageRepository.save(m);
                saved++;
            }
        } catch (Exception e) {
            log("FETCH", "ERROR", "binance: " + e.getMessage());
        }
        try {
            // CoinGecko status updates
            ResponseEntity<Map> resp = restTemplate.getForEntity("https://api.coingecko.com/api/v3/status_updates?per_page=3", Map.class);
            Object updates = resp.getBody() != null ? resp.getBody().get("status_updates") : null;
            if (updates instanceof List) {
                for (Object u : (List) updates) {
                    if (!(u instanceof Map)) continue;
                    String desc = String.valueOf(((Map) u).getOrDefault("description", ""));
                    String symbol = String.valueOf(((Map) u).getOrDefault("project", Collections.emptyMap()) instanceof Map ? ((Map)((Map) u).get("project")).getOrDefault("symbol", "") : "");
                    if (desc.isEmpty()) continue;
                    String title = "Update: " + desc;
                    if (messageRepository.existsByTitle(title)) continue;
                    Message m = new Message();
                    m.setTitle(title);
                    m.setSymbol(symbol);
                    m.setSentiment("中性");
                    m.setSourceUrl(String.valueOf(((Map) u).getOrDefault("article_link", "https://www.coingecko.com/en")));
                    m.setCreatedAt(java.time.OffsetDateTime.now());
                    m.setReadFlag(false);
                    messageRepository.save(m);
                    saved++;
                }
            }
        } catch (Exception e) {
            log("FETCH", "ERROR", "coingecko_status: " + e.getMessage());
        }
        if (saved == 0) {
            log("FETCH", "EMPTY", "no new external messages");
        } else {
            log("FETCH", "SUCCESS", "new messages=" + saved);
        }
    }

    private boolean isValidResult(WorkflowResult r) {
        if (isBlank(r.getSummary())) return false;
        boolean hasStructured = !isBlank(r.getPlanJson()) || !isBlank(r.getAnalysisJson()) || !isBlank(r.getPositionsSnapshotJson()) || !isBlank(r.getAdjustmentsJson());
        if (!hasStructured) return false;
        try {
            if (!isBlank(r.getPlanJson())) {
                objectMapper.readTree(r.getPlanJson());
            }
            if (!isBlank(r.getAnalysisJson())) {
                objectMapper.readTree(r.getAnalysisJson());
            }
            invalidCount.set(0);
        } catch (Exception e) {
            int cnt = invalidCount.incrementAndGet();
            log("WORKFLOW_OUTPUT", "INVALID_JSON", e.getMessage() + ", count=" + cnt);
            if (cnt < 2) {
                // 尝试简单标准化：去除首尾反引号/反斜杠
                try {
                    if (!isBlank(r.getPlanJson())) {
                        String fixed = r.getPlanJson().replaceAll("`", "");
                        objectMapper.readTree(fixed);
                        r.setPlanJson(fixed);
                        invalidCount.set(0);
                        return true;
                    }
                } catch (Exception ignored) {}
            }
            return false;
        }
        return true;
    }

    private static class WorkflowResult {
        private String summary;
        private String planJson;
        private String sentiment;
        private String analysisJson;
        private String positionsSnapshotJson;
        private String adjustmentsJson;
        private String riskNotes;
        private String confidence;
        private String impactStrength;
        private String keyPoints;

        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getPlanJson() { return planJson; }
        public void setPlanJson(String planJson) { this.planJson = planJson; }
        public String getSentiment() { return sentiment; }
        public void setSentiment(String sentiment) { this.sentiment = sentiment; }
        public String getAnalysisJson() { return analysisJson; }
        public void setAnalysisJson(String analysisJson) { this.analysisJson = analysisJson; }
        public String getPositionsSnapshotJson() { return positionsSnapshotJson; }
        public void setPositionsSnapshotJson(String positionsSnapshotJson) { this.positionsSnapshotJson = positionsSnapshotJson; }
        public String getAdjustmentsJson() { return adjustmentsJson; }
        public void setAdjustmentsJson(String adjustmentsJson) { this.adjustmentsJson = adjustmentsJson; }
        public String getRiskNotes() { return riskNotes; }
        public void setRiskNotes(String riskNotes) { this.riskNotes = riskNotes; }
        public String getConfidence() { return confidence; }
        public void setConfidence(String confidence) { this.confidence = confidence; }
        public String getImpactStrength() { return impactStrength; }
        public void setImpactStrength(String impactStrength) { this.impactStrength = impactStrength; }
        public String getKeyPoints() { return keyPoints; }
        public void setKeyPoints(String keyPoints) { this.keyPoints = keyPoints; }
    }
}
