package com.aiinvest.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String title;
    private String symbol;
    private String sentiment;
    private String sourceUrl;
    @Column(columnDefinition = "LONGTEXT")
    private String content;
    @Column(columnDefinition = "LONGTEXT")
    private String summary;
    @Column(columnDefinition = "LONGTEXT")
    private String impactDescription;
    @Column(nullable = false)
    private OffsetDateTime createdAt;
    @Column(nullable = false)
    private boolean readFlag = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getImpactDescription() { return impactDescription; }
    public void setImpactDescription(String impactDescription) { this.impactDescription = impactDescription; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public boolean isReadFlag() { return readFlag; }
    public void setReadFlag(boolean readFlag) { this.readFlag = readFlag; }
}
