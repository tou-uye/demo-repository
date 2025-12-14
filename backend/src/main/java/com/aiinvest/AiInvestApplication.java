package com.aiinvest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import io.github.cdimascio.dotenv.Dotenv;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class AiInvestApplication {
    public static void main(String[] args) {
        try {
            // Prefer backend/.env when launched from repo root; fall back to CWD .env.
            Dotenv dotenvBackend = Dotenv.configure().ignoreIfMissing().directory("backend").load();
            applyDotenv(dotenvBackend);

            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            applyDotenv(dotenv);
        } catch (Exception ignored) {}
        SpringApplication.run(AiInvestApplication.class, args);
    }

    private static void applyDotenv(Dotenv dotenv) {
        if (dotenv == null) return;
        setIfAbsent("DIFY_API_KEY", dotenv.get("DIFY_API_KEY"));
        setIfAbsent("DIFY_FIRST_API_KEY", dotenv.get("DIFY_FIRST_API_KEY"));
        setIfAbsent("DIFY_SECOND_API_KEY", dotenv.get("DIFY_SECOND_API_KEY"));
        setIfAbsent("DIFY_FIX_API_KEY", dotenv.get("DIFY_FIX_API_KEY"));
        setIfAbsent("DIFY_BASE_URL", dotenv.get("DIFY_BASE_URL"));
        setIfAbsent("DIFY_WORKFLOW_ID", dotenv.get("DIFY_WORKFLOW_ID"));
        setIfAbsent("DIFY_FIRST_WORKFLOW_ID", dotenv.get("DIFY_FIRST_WORKFLOW_ID"));
        setIfAbsent("DIFY_SECOND_WORKFLOW_ID", dotenv.get("DIFY_SECOND_WORKFLOW_ID"));
        setIfAbsent("DIFY_FIX_WORKFLOW_ID", dotenv.get("DIFY_FIX_WORKFLOW_ID"));
        setIfAbsent("DIFY_INVALID_THRESHOLD", dotenv.get("DIFY_INVALID_THRESHOLD"));
        setIfAbsent("MYSQL_URL", dotenv.get("MYSQL_URL"));
        setIfAbsent("MYSQL_USERNAME", dotenv.get("MYSQL_USERNAME"));
        setIfAbsent("MYSQL_PASSWORD", dotenv.get("MYSQL_PASSWORD"));
        setIfAbsent("RETRY_MAX_ATTEMPTS", dotenv.get("RETRY_MAX_ATTEMPTS"));
        setIfAbsent("RETRY_DELAY_MS", dotenv.get("RETRY_DELAY_MS"));
        setIfAbsent("COLLECT_CRON", dotenv.get("COLLECT_CRON"));
        setIfAbsent("SNAPSHOT_CRON", dotenv.get("SNAPSHOT_CRON"));
        setIfAbsent("SERVER_PORT", dotenv.get("SERVER_PORT"));
        setIfAbsent("HTTP_CONNECT_TIMEOUT_MS", dotenv.get("HTTP_CONNECT_TIMEOUT_MS"));
        setIfAbsent("HTTP_READ_TIMEOUT_MS", dotenv.get("HTTP_READ_TIMEOUT_MS"));
        setIfAbsent("COLLECT_MAX_ANALYZE", dotenv.get("COLLECT_MAX_ANALYZE"));
        setIfAbsent("COLLECT_MAX_ATTEMPT", dotenv.get("COLLECT_MAX_ATTEMPT"));
        setIfAbsent("COINGECKO_ENABLED", dotenv.get("COINGECKO_ENABLED"));
        setIfAbsent("COINGECKO_BASE_URL", dotenv.get("COINGECKO_BASE_URL"));
        setIfAbsent("BINANCE_ENABLED", dotenv.get("BINANCE_ENABLED"));
        setIfAbsent("BINANCE_BASE_URL", dotenv.get("BINANCE_BASE_URL"));
        setIfAbsent("RSS_MAX_ITEMS", dotenv.get("RSS_MAX_ITEMS"));
        setIfAbsent("BINANCE_RSS_ENABLED", dotenv.get("BINANCE_RSS_ENABLED"));
        setIfAbsent("BINANCE_RSS_URL", dotenv.get("BINANCE_RSS_URL"));
        setIfAbsent("COINDESK_RSS_ENABLED", dotenv.get("COINDESK_RSS_ENABLED"));
        setIfAbsent("COINDESK_RSS_URL", dotenv.get("COINDESK_RSS_URL"));
    }

    @Bean
    public RestTemplate restTemplate(
            RestTemplateBuilder builder,
            @Value("${http.connectTimeoutMs:3000}") long connectTimeoutMs,
            @Value("${http.readTimeoutMs:15000}") long readTimeoutMs
    ) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    @Bean(name = "collectExecutor")
    public Executor collectExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("collect-executor");
            t.setDaemon(true);
            return t;
        });
    }

    private static void setIfAbsent(String key, String value) {
        if (value != null && System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }
}
