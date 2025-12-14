package com.aiinvest.service;

import com.aiinvest.domain.Message;
import com.aiinvest.domain.Report;
import com.aiinvest.repo.MessageRepository;
import com.aiinvest.repo.OperationLogRepository;
import com.aiinvest.repo.PositionRepository;
import com.aiinvest.repo.ReportRepository;
import com.aiinvest.service.PositionBatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.data.domain.PageRequest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Map;
import java.util.Collections;

@Service
public class DifyService {
    @Value("${dify.apiKey:}")
    private String apiKey;
    @Value("${dify.firstApiKey:}")
    private String firstApiKey;
    @Value("${dify.secondApiKey:}")
    private String secondApiKey;
    @Value("${dify.fixApiKey:}")
    private String fixApiKey;
    @Value("${dify.workflowId:}")
    private String workflowId;
    @Value("${dify.fixWorkflowId:}")
    private String fixWorkflowId;
    @Value("${dify.invalidThreshold:3}")
    private int invalidThreshold;
    @Value("${dify.firstWorkflowId:}")
    private String firstWorkflowId;
    @Value("${dify.secondWorkflowId:}")
    private String secondWorkflowId;
    @Value("${dify.baseUrl:https://api.dify.ai/v1}")
    private String baseUrl;
    private final RestTemplate restTemplate;
    private final MessageRepository messageRepository;
    private final OperationLogRepository operationLogRepository;
    private final ReportRepository reportRepository;
    private final PositionRepository positionRepository;
    private final PositionBatchService positionBatchService;
    @Value("${retry.maxAttempts:3}")
    private int maxAttempts;
    @Value("${retry.delayMillis:2000}")
    private long delayMillis;
    @Value("${collect.maxAnalyze:20}")
    private int maxAnalyze;
    @Value("${collect.maxAttempt:50}")
    private int maxAttempt;
    @Value("${fetch.coingecko.enabled:true}")
    private boolean coingeckoEnabled;
    @Value("${fetch.coingecko.baseUrl:https://api.coingecko.com/api/v3}")
    private String coingeckoBaseUrl;
    // Binance ticker is low-signal; keep optional (RSS is preferred)
    @Value("${fetch.binance.enabled:false}")
    private boolean binanceEnabled;
    @Value("${fetch.binance.baseUrl:https://data-api.binance.vision/api/v3}")
    private String binanceBaseUrl;
    @Value("${fetch.rss.binance.enabled:true}")
    private boolean binanceRssEnabled;
    @Value("${fetch.rss.binance.url:https://www.binance.com/en/support/announcement/rss}")
    private String binanceRssUrl;
    @Value("${fetch.rss.coindesk.enabled:true}")
    private boolean coindeskRssEnabled;
    @Value("${fetch.rss.coindesk.url:https://www.coindesk.com/arc/outboundfeeds/rss/}")
    private String coindeskRssUrl;
    @Value("${fetch.rss.maxItems:15}")
    private int rssMaxItems;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final java.util.concurrent.atomic.AtomicInteger invalidCount = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger fetchFailStreak = new java.util.concurrent.atomic.AtomicInteger(0);

    public DifyService(RestTemplate restTemplate, MessageRepository messageRepository, OperationLogRepository operationLogRepository, ReportRepository reportRepository, PositionRepository positionRepository, PositionBatchService positionBatchService) {
        this.restTemplate = restTemplate;
        this.messageRepository = messageRepository;
        this.operationLogRepository = operationLogRepository;
        this.reportRepository = reportRepository;
        this.positionRepository = positionRepository;
        this.positionBatchService = positionBatchService;
    }

    @PostConstruct
    public void validateConfig() {
        boolean hasAnyWorkflow = !isBlank(workflowId) || !isBlank(firstWorkflowId) || !isBlank(secondWorkflowId);
        boolean hasAnyApiKey = !isBlank(apiKey) || !isBlank(firstApiKey) || !isBlank(secondApiKey) || !isBlank(fixApiKey);
        if (!hasAnyApiKey || !hasAnyWorkflow) {
            // Allow app to start without Dify (e.g., only collecting external RSS/messages).
            // Dify-dependent features will simply be skipped.
            log("DIFY", "DISABLED", "missing apiKey/workflowId; AI analysis disabled until configured");
        }
    }

    public int collectAndAnalyze() {
        boolean hasAnyWorkflow = !isBlank(workflowId) || !isBlank(firstWorkflowId) || !isBlank(secondWorkflowId);
        boolean hasAnyApiKey = !isBlank(apiKey) || !isBlank(firstApiKey) || !isBlank(secondApiKey) || !isBlank(fixApiKey);
        collectExternalSources();
        if (!hasAnyApiKey || !hasAnyWorkflow) {
            // Allow collecting external messages even when Dify is not configured/available.
            log("DIFY", "SKIP", "AI analysis skipped due to missing apiKey/workflowId");
            logCollect(0);
            return 0;
        }

        int processed = 0;
        int limit = maxAttempt > 0 ? maxAttempt : 50;
        List<Message> messages = messageRepository.findUnreported(PageRequest.of(0, limit));
        for (Message m : messages) {
            if (m.getId() == null) continue;
            // 首轮分析
            WorkflowResult first = runFirstAnalysis(m);
            if (!isBlankOrNone(first.getSentiment())) {
                m.setSentiment(first.getSentiment());
            }
            if (!isBlankOrNone(first.getTargetSymbol())) {
                m.setSymbol(first.getTargetSymbol());
            }
            if (!isBlankOrNone(first.getSourceUrl())) {
                m.setSourceUrl(first.getSourceUrl());
            }
            if (!isBlank(first.getKeyPoints())) {
                m.setImpactDescription(first.getKeyPoints());
                if (isBlank(m.getSummary())) {
                    m.setSummary(first.getKeyPoints());
                }
            }
            if (!isBlank(first.getImpactStrength())) {
                if (isBlank(m.getSummary())) {
                    String s = (first.getSentiment() != null ? first.getSentiment() : "") + " " + first.getImpactStrength();
                    m.setSummary(s.trim());
                }
            }
            messageRepository.save(m);
            // 二次分析
            WorkflowResult result = runSecondAnalysis(m, first);
            if (!isValidResult(result)) {
                log("WORKFLOW_OUTPUT", "INVALID", "messageId=" + m.getId());
                continue;
            }
            Report r = new Report();
            r.setSummary(trimSummary(result.getSummary()));
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
            if (maxAnalyze > 0 && processed >= maxAnalyze) {
                break;
            }
        }
        logCollect(processed);
        return processed;
    }

    private WorkflowResult runFirstAnalysis(Message m) {
        Map<String, Object> inputs = new java.util.HashMap<>();
        inputs.put("title", m.getTitle());
        inputs.put("symbol", m.getSymbol());
        String wf = !isBlank(firstWorkflowId) ? firstWorkflowId : workflowId;
        String key = !isBlank(firstApiKey) ? firstApiKey : apiKey;
        WorkflowResult result = callWorkflow(wf, inputs, "first-pass", key);
        if (result.getSummary() == null) {
            result.setSummary(m.getTitle());
        }
        return result;
    }

    private WorkflowResult runSecondAnalysis(Message m, WorkflowResult first) {
        Map<String, Object> inputs = new java.util.HashMap<>();
        inputs.put("title", m.getTitle());
        inputs.put("symbol", m.getSymbol());
        // Dify 二次分析表单将 sentiment 设为必填，若首轮未给出则兜底为中性，避免 400
        String secondSentiment = !isBlank(first.getSentiment()) ? first.getSentiment() : "中性";
        inputs.put("sentiment", secondSentiment);
        if (!isBlank(first.getAnalysisJson())) inputs.put("analysis", first.getAnalysisJson());
        // Prefer readable key points / content as analysis text for second-pass prompt
        if (!isBlank(first.getKeyPoints())) inputs.put("analysis_text", first.getKeyPoints());
        else if (!isBlank(m.getContent())) inputs.put("analysis_text", truncate(stripHtml(m.getContent()), 1200));
        else if (!isBlank(first.getSummary())) inputs.put("analysis_text", first.getSummary());
        try {
            // Dify input form type is text-input; it expects string, not array/object.
            inputs.put("positions", toJson(positionBatchService.currentPositions()));
        } catch (Exception ignored) {}
        String wf = !isBlank(secondWorkflowId) ? secondWorkflowId : workflowId;
        String key = !isBlank(secondApiKey) ? secondApiKey : apiKey;
        return callWorkflow(wf, inputs, "second-pass", key);
    }

    private WorkflowResult callWorkflow(String wfId, Map<String, Object> inputs, String userLabel, String bearerApiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearerApiKey);
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("inputs", inputs);
        payload.put("workflow_id", wfId);
        payload.put("response_mode", "blocking");
        payload.put("user", "system-" + userLabel);

        final String url = baseUrl + "/workflows/run";
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        int attempts = 0;
        while (attempts < maxAttempts) {
            attempts++;
            try {
                // Dify Workflow API (Cloud/Self-host): POST {baseUrl}/workflows/run with workflow_id in payload
                ResponseEntity<Map> resp = restTemplate.postForEntity(url, entity, Map.class);
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
            } catch (HttpStatusCodeException e) {
                log("WORKFLOW_OUTPUT", "HTTP_" + e.getStatusCode().value(),
                        "url=" + url + ", workflowId=" + wfId + ", status=" + e.getStatusCode().value()
                                + ", body=" + truncate(e.getResponseBodyAsString(), 800));
                if (e.getStatusCode().is4xxClientError() && e.getStatusCode().value() != 429) {
                    break;
                }
                try { Thread.sleep(delayMillis); } catch (InterruptedException ignored) {}
            } catch (Exception e) {
                try { Thread.sleep(delayMillis); } catch (InterruptedException ignored) {}
            }
        }
        // 兜底尝试：调用修复工作流或加请求标记重试一次
        WorkflowResult fix = tryFixWorkflow(inputs, userLabel);
        if (fix != null) {
            return fix;
        }
        WorkflowResult fallback = new WorkflowResult();
        fallback.setSummary(String.valueOf(inputs.getOrDefault("title", "")));
        return fallback;
    }

    private WorkflowResult tryFixWorkflow(Map<String, Object> inputs, String userLabel) {
        if (isBlank(fixWorkflowId) && isBlank(secondWorkflowId)) return null;
        String wf = !isBlank(fixWorkflowId) ? fixWorkflowId : secondWorkflowId;
        String bearerApiKey = !isBlank(fixApiKey)
                ? fixApiKey
                : (!isBlank(secondApiKey) ? secondApiKey : apiKey);
        Map<String, Object> fixedInputs = new java.util.HashMap<>(inputs);
        fixedInputs.put("request_fix", true);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(bearerApiKey);
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("inputs", fixedInputs);
            payload.put("response_mode", "blocking");
            payload.put("user", "system-fix-" + userLabel);
            payload.put("workflow_id", wf);
            final String url = baseUrl + "/workflows/run";
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> resp = restTemplate.postForEntity(url, entity, Map.class);
            Map body = resp.getBody();
            Object data = body != null ? body.get("data") : null;
            Object outputs = data instanceof Map ? ((Map) data).get("outputs") : null;
            WorkflowResult result = parseOutputs(outputs);
            if (!isBlank(result.getSummary()) || !isBlank(result.getPlanJson()) || !isBlank(result.getAnalysisJson())) {
                log("WORKFLOW_OUTPUT", "FIXED", "used fix workflow");
                return result;
            }
        } catch (HttpStatusCodeException e) {
            log("WORKFLOW_OUTPUT", "FIX_HTTP_" + e.getStatusCode().value(),
                    "url=" + (baseUrl + "/workflows/run") + ", workflowId=" + wf + ", status=" + e.getStatusCode().value()
                            + ", body=" + truncate(e.getResponseBodyAsString(), 800));
        } catch (Exception e) {
            log("WORKFLOW_OUTPUT", "FIX_FAILED", String.valueOf(e.getMessage()));
        }
        return null;
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

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...(truncated)";
    }

    private String trimSummary(String s) {
        if (s == null) return "";
        String v = s.trim();
        int limit = 250; // 防止 DB 字段过短导致插入失败
        return v.length() > limit ? v.substring(0, limit) : v;
    }

    private String stripHtml(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean isBlankOrNone(String s) {
        if (s == null) return true;
        String t = s.trim();
        return t.isEmpty() || "NONE".equalsIgnoreCase(t) || "NULL".equalsIgnoreCase(t);
    }

    private String normalizeOptionalString(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return isBlankOrNone(s) ? null : s;
    }

    private Object pick(Map map, String... keys) {
        if (map == null) return null;
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null) return v;
        }
        return null;
    }

    private WorkflowResult parseOutputs(Object outputs) {
        WorkflowResult result = new WorkflowResult();
        if (outputs == null) return result;
        try {
            if (outputs instanceof Map) {
                Map outMap = (Map) outputs;
                Object plan = outMap.get("plan");
                Object analysis = outMap.get("analysis");
                Object sentiment = pick(outMap, "sentiment");
                Object targetSymbol = pick(outMap, "target_symbol", "targetSymbol");
                Object sourceUrl = pick(outMap, "source_url", "sourceUrl");
                Object positionsSnapshot = pick(outMap, "positions_snapshot", "positions_sr");
                Object adjustments = pick(outMap, "adjustments");
                Object riskNotes = pick(outMap, "risk_notes", "riskNotes");
                Object confidence = pick(outMap, "confidence");
                Object impactStrength = pick(outMap, "impact_strength", "impact_streng", "impactStrength");
                Object keyPoints = pick(outMap, "key_points", "keyPoints");
                if (analysis instanceof Map && sentiment == null) {
                    Object s = ((Map) analysis).get("sentiment");
                    if (s != null) sentiment = s;
                    Object ts = ((Map) analysis).get("target_symbol");
                    if (ts != null) targetSymbol = ts;
                    Object su = ((Map) analysis).get("source_url");
                    if (su != null) sourceUrl = su;
                }
                String senti = normalizeOptionalString(sentiment);
                String ts = normalizeOptionalString(targetSymbol);
                String su = normalizeOptionalString(sourceUrl);
                String impact = normalizeOptionalString(impactStrength);
                if (senti != null) result.setSentiment(senti);
                if (ts != null) result.setTargetSymbol(ts);
                if (su != null) result.setSourceUrl(su);
                if (impact != null) result.setImpactStrength(impact);
                if (keyPoints != null) result.setKeyPoints(stringify(keyPoints));
                if (positionsSnapshot != null) result.setPositionsSnapshotJson(toJson(positionsSnapshot));
                if (adjustments != null) result.setAdjustmentsJson(toJson(adjustments));
                String rn = normalizeOptionalString(riskNotes);
                String conf = normalizeOptionalString(confidence);
                if (rn != null) result.setRiskNotes(rn);
                if (conf != null) result.setConfidence(conf);
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
                // First-pass workflows often output {sentiment,key_points,...} directly.
                // Keep a structured JSON string for downstream usage.
                result.setAnalysisJson(toJson(outMap));
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
        int failed = 0;
        // Prefer RSS sources for high-signal, stable content.
        try {
            if (binanceRssEnabled && !isBlank(binanceRssUrl)) {
                int n = fetchRss(binanceRssUrl, "Binance公告", rssMaxItems);
                saved += n;
                if (n > 0) log("FETCH", "SUCCESS", "binance_rss new messages=" + n);
            }
        } catch (Exception e) {
            log("FETCH", "ERROR", "binance_rss: " + e.getMessage());
            failed++;
        }
        try {
            if (coindeskRssEnabled && !isBlank(coindeskRssUrl)) {
                int n = fetchRss(coindeskRssUrl, "CoinDesk", rssMaxItems);
                saved += n;
                if (n > 0) log("FETCH", "SUCCESS", "coindesk_rss new messages=" + n);
            }
        } catch (Exception e) {
            log("FETCH", "ERROR", "coindesk_rss: " + e.getMessage());
            failed++;
        }

        if (coingeckoEnabled) {
            try {
                // CoinGecko trending
                ResponseEntity<Map> resp = restTemplate.getForEntity(coingeckoBaseUrl + "/search/trending", Map.class);
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
                failed++;
            }
        }
        if (binanceEnabled) {
            try {
                // Binance price
                ResponseEntity<Map> resp = restTemplate.getForEntity(binanceBaseUrl + "/ticker/price?symbol=BTCUSDT", Map.class);
                Object price = resp.getBody() != null ? resp.getBody().get("price") : null;
                String title = "Binance price: BTCUSDT " + String.valueOf(price);
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
                failed++;
            }
        }
        if (coingeckoEnabled) {
            try {
                // CoinGecko status updates
                ResponseEntity<Map> resp = restTemplate.getForEntity(coingeckoBaseUrl + "/status_updates?per_page=3", Map.class);
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
                failed++;
            }
        }
        if (coingeckoEnabled) {
            try {
                // CoinGecko news feed (task requires ≥2 sources; news as third)
                ResponseEntity<Map> resp = restTemplate.getForEntity(coingeckoBaseUrl + "/news", Map.class);
            Object news = resp.getBody() != null ? resp.getBody().get("data") : null;
            if (news instanceof List) {
                for (Object n : (List) news) {
                    if (!(n instanceof Map)) continue;
                    Map row = (Map) n;
                    String title = String.valueOf(row.getOrDefault("title", ""));
                    if (title.isEmpty()) continue;
                    String url = String.valueOf(row.getOrDefault("url", row.getOrDefault("news_url", "https://www.coingecko.com/en/news")));
                    if (messageRepository.existsByTitle(title)) continue;
                    String symbol = String.valueOf(((Map) row.getOrDefault("project", Collections.emptyMap())) instanceof Map ? ((Map)((Map) row.get("project"))).getOrDefault("symbol", "") : "");
                    Message m = new Message();
                    m.setTitle(title);
                    m.setSymbol(symbol);
                    m.setSentiment("中性");
                    m.setSourceUrl(url);
                    m.setCreatedAt(java.time.OffsetDateTime.now());
                    m.setReadFlag(false);
                    messageRepository.save(m);
                    saved++;
                }
            }
            } catch (Exception e) {
                log("FETCH", "ERROR", "coingecko_news: " + e.getMessage());
                failed++;
            }
        }
        if (saved == 0) {
            if (failed == 0) {
                // 任务要求：无新增消息时生成“今日无新增消息”记录，供前端查看
                String todayTitle = "今日无新增消息 " + java.time.LocalDate.now();
                if (!messageRepository.existsByTitle(todayTitle)) {
                    Message m = new Message();
                    m.setTitle(todayTitle);
                    m.setSymbol("");
                    m.setSentiment("");
                    m.setSourceUrl("https://www.binance.com/en/support/announcement");
                    m.setContent("");
                    m.setSummary("");
                    m.setImpactDescription("");
                    m.setCreatedAt(java.time.OffsetDateTime.now());
                    m.setReadFlag(false);
                    messageRepository.save(m);
                }
                log("FETCH", "EMPTY", "no new external messages");
            }
        } else {
            if (failed == 0) {
                log("FETCH", "SUCCESS", "new messages=" + saved);
            }
        }

        if (failed > 0) {
            int streak = fetchFailStreak.incrementAndGet();
            log("FETCH", "ERROR", "fetch errors this run=" + failed + ", new messages=" + saved + ", consecutiveFailedRuns=" + streak);
            if (streak >= 2) {
                log("FETCH", "ALERT", "fetch failed consecutively (runs)=" + streak + ", check external sources or network");
            }
        } else {
            fetchFailStreak.set(0);
        }
    }

    private int fetchRss(String url, String sourceLabel, int maxItems) throws Exception {
        int limit = maxItems > 0 ? maxItems : 15;
        ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
        String xml = resp.getBody();
        if (xml == null || xml.trim().isEmpty()) return 0;

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        // Secure XML parsing (avoid XXE)
        try { dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) {}
        try { dbf.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) {}
        try { dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) {}
        try { dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); } catch (Exception ignored) {}
        try { dbf.setXIncludeAware(false); } catch (Exception ignored) {}
        try { dbf.setExpandEntityReferences(false); } catch (Exception ignored) {}

        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList items = doc.getElementsByTagName("item");
        if (items == null || items.getLength() == 0) return 0;

        int saved = 0;
        int count = Math.min(items.getLength(), limit);
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        for (int i = 0; i < count; i++) {
            if (!(items.item(i) instanceof Element item)) continue;
            String rawTitle = textOf(item, "title");
            String link = textOf(item, "link");
            if (isBlank(rawTitle) || isBlank(link)) continue;
            String title = sourceLabel + ": " + rawTitle.trim();
            String sourceUrl = link.trim();
            if (messageRepository.existsBySourceUrl(sourceUrl) || messageRepository.existsByTitle(title)) continue;

            String desc = textOf(item, "description");
            if (isBlank(desc)) {
                desc = textOf(item, "content:encoded");
            }
            Message m = new Message();
            m.setTitle(title);
            m.setSymbol(guessSymbol(rawTitle));
            m.setSourceUrl(sourceUrl);
            if (!isBlank(desc)) {
                String plain = stripHtml(desc);
                m.setContent(plain);
                m.setSummary(truncate(plain, 240));
            }
            m.setCreatedAt(now);
            m.setReadFlag(false);
            messageRepository.save(m);
            saved++;
        }
        return saved;
    }

    private String textOf(Element parent, String tag) {
        try {
            NodeList nodes = parent.getElementsByTagName(tag);
            if (nodes == null || nodes.getLength() == 0) return "";
            String t = nodes.item(0).getTextContent();
            return t == null ? "" : t;
        } catch (Exception ignored) {
            return "";
        }
    }

    private String guessSymbol(String title) {
        if (title == null) return "";
        String t = title.toUpperCase();
        if (t.contains(" BTC")) return "BTC";
        if (t.contains(" ETH")) return "ETH";
        if (t.contains(" SOL")) return "SOL";
        if (t.contains(" USDT")) return "USDT";
        if (t.contains("BTC")) return "BTC";
        if (t.contains("ETH")) return "ETH";
        if (t.contains("SOL")) return "SOL";
        if (t.contains("USDT")) return "USDT";
        return "";
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
            if (isBlank(r.getPositionsSnapshotJson()) && isBlank(r.getAdjustmentsJson())) {
                return false;
            }
            invalidCount.set(0);
        } catch (Exception e) {
            int cnt = invalidCount.incrementAndGet();
            log("WORKFLOW_OUTPUT", "INVALID_JSON", e.getMessage() + ", count=" + cnt);
            if (cnt >= invalidThreshold) {
                log("WORKFLOW_OUTPUT", "ALERT", "invalid outputs >= " + invalidThreshold + ", pause generation for manual check");
                return false;
            }
            // 尝试简单标准化：去除反引号
            try {
                if (!isBlank(r.getPlanJson())) {
                    String fixed = r.getPlanJson().replaceAll("`", "");
                    objectMapper.readTree(fixed);
                    r.setPlanJson(fixed);
                    invalidCount.set(0);
                    return true;
                }
            } catch (Exception ignored) {}
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
        private String targetSymbol;
        private String sourceUrl;

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
        public String getTargetSymbol() { return targetSymbol; }
        public void setTargetSymbol(String targetSymbol) { this.targetSymbol = targetSymbol; }
        public String getSourceUrl() { return sourceUrl; }
        public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    }
}
