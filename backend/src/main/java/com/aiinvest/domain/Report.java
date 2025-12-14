package com.aiinvest.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String summary;
    @Column(nullable = false)
    private String status;
    private Long messageId;
    @Column(nullable = false)
    private OffsetDateTime createdAt;
    @Column(columnDefinition = "LONGTEXT")
    private String planJson;
    private String reviewReason;
    private String reviewer;
    private OffsetDateTime reviewedAt;
    @Column(columnDefinition = "LONGTEXT")
    private String analysisJson;
    @Column(columnDefinition = "LONGTEXT")
    private String positionsSnapshotJson;
    @Column(columnDefinition = "LONGTEXT")
    private String adjustmentsJson;
    private String riskNotes;
    private String confidence;
    private String sentiment;
    private String impactStrength;
    private String keyPoints;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public String getPlanJson() { return planJson; }
    public void setPlanJson(String planJson) { this.planJson = planJson; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
    public String getReviewer() { return reviewer; }
    public void setReviewer(String reviewer) { this.reviewer = reviewer; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
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
    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }
    public String getImpactStrength() { return impactStrength; }
    public void setImpactStrength(String impactStrength) { this.impactStrength = impactStrength; }
    public String getKeyPoints() { return keyPoints; }
    public void setKeyPoints(String keyPoints) { this.keyPoints = keyPoints; }
}
