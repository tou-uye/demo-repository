package com.aiinvest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
@EnableScheduling
public class AiInvestApplication {
    public static void main(String[] args) {
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            setIfAbsent("DIFY_API_KEY", dotenv.get("DIFY_API_KEY"));
            setIfAbsent("DIFY_BASE_URL", dotenv.get("DIFY_BASE_URL"));
            setIfAbsent("DIFY_WORKFLOW_ID", dotenv.get("DIFY_WORKFLOW_ID"));
            setIfAbsent("MYSQL_URL", dotenv.get("MYSQL_URL"));
            setIfAbsent("MYSQL_USERNAME", dotenv.get("MYSQL_USERNAME"));
            setIfAbsent("MYSQL_PASSWORD", dotenv.get("MYSQL_PASSWORD"));
            setIfAbsent("RETRY_MAX_ATTEMPTS", dotenv.get("RETRY_MAX_ATTEMPTS"));
            setIfAbsent("RETRY_DELAY_MS", dotenv.get("RETRY_DELAY_MS"));
        } catch (Exception ignored) {}
        SpringApplication.run(AiInvestApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    private static void setIfAbsent(String key, String value) {
        if (value != null && System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }
}
