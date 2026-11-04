package com.smxplore.users.infrastructure.adapter.out.persistence.mongo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("reviews")
@CompoundIndexes({
    @CompoundIndex(
            name = "ux_reviews_tourist_target",
            def = "{'touristId': 1, 'targetType': 1, 'targetId': 1}",
            unique = true),
    @CompoundIndex(
            name = "ix_reviews_target",
            def = "{'targetType': 1, 'targetId': 1, 'moderationStatus': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "ix_reviews_reported", def = "{'reported': 1, 'updatedAt': -1}"),
    @CompoundIndex(name = "ix_reviews_ai_flagged", def = "{'aiFlagged': 1, 'moderationStatus': 1, 'createdAt': -1}")
})
public class ReviewDocument {

    @Id
    private String id;

    private UUID touristId;
    private String targetType;
    private UUID targetId;
    private int rating;
    private String comment;
    private Response response;
    private Instant createdAt;
    private Instant updatedAt;
    private String moderationStatus;
    private boolean reported;
    private List<Report> reports = new ArrayList<>();
    private UUID moderatedBy;
    private Instant moderatedAt;
    private String moderationReason;
    private String aiStatus;
    private boolean aiFlagged;
    private double aiScore;
    private List<String> aiCategories = new ArrayList<>();
    private String aiReason;
    private String aiModel;
    private Instant aiCheckedAt;

    public ReviewDocument() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UUID getTouristId() {
        return touristId;
    }

    public void setTouristId(UUID touristId) {
        this.touristId = touristId;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public void setTargetId(UUID targetId) {
        this.targetId = targetId;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Response getResponse() {
        return response;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getModerationStatus() {
        return moderationStatus;
    }

    public void setModerationStatus(String moderationStatus) {
        this.moderationStatus = moderationStatus;
    }

    public boolean isReported() {
        return reported;
    }

    public void setReported(boolean reported) {
        this.reported = reported;
    }

    public List<Report> getReports() {
        return reports;
    }

    public void setReports(List<Report> reports) {
        this.reports = reports;
    }

    public UUID getModeratedBy() {
        return moderatedBy;
    }

    public void setModeratedBy(UUID moderatedBy) {
        this.moderatedBy = moderatedBy;
    }

    public Instant getModeratedAt() {
        return moderatedAt;
    }

    public void setModeratedAt(Instant moderatedAt) {
        this.moderatedAt = moderatedAt;
    }

    public String getModerationReason() {
        return moderationReason;
    }

    public void setModerationReason(String moderationReason) {
        this.moderationReason = moderationReason;
    }

    public String getAiStatus() {
        return aiStatus;
    }

    public void setAiStatus(String aiStatus) {
        this.aiStatus = aiStatus;
    }

    public boolean isAiFlagged() {
        return aiFlagged;
    }

    public void setAiFlagged(boolean aiFlagged) {
        this.aiFlagged = aiFlagged;
    }

    public double getAiScore() {
        return aiScore;
    }

    public void setAiScore(double aiScore) {
        this.aiScore = aiScore;
    }

    public List<String> getAiCategories() {
        return aiCategories;
    }

    public void setAiCategories(List<String> aiCategories) {
        this.aiCategories = aiCategories;
    }

    public String getAiReason() {
        return aiReason;
    }

    public void setAiReason(String aiReason) {
        this.aiReason = aiReason;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public Instant getAiCheckedAt() {
        return aiCheckedAt;
    }

    public void setAiCheckedAt(Instant aiCheckedAt) {
        this.aiCheckedAt = aiCheckedAt;
    }

    public static class Response {

        private UUID responderId;
        private String text;
        private Instant createdAt;
        private Instant updatedAt;

        public Response() {}

        public UUID getResponderId() {
            return responderId;
        }

        public void setResponderId(UUID responderId) {
            this.responderId = responderId;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }

        public Instant getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    public static class Report {

        private UUID reporterId;
        private String reason;
        private Instant reportedAt;

        public Report() {}

        public UUID getReporterId() {
            return reporterId;
        }

        public void setReporterId(UUID reporterId) {
            this.reporterId = reporterId;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public Instant getReportedAt() {
            return reportedAt;
        }

        public void setReportedAt(Instant reportedAt) {
            this.reportedAt = reportedAt;
        }
    }
}
