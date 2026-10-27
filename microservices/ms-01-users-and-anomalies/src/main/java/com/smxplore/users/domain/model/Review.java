package com.smxplore.users.domain.model;

import com.smxplore.users.domain.exception.ReviewAlreadyReportedException;
import com.smxplore.users.domain.exception.ValidationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Reseña de un turista sobre un guía o una prestadora de servicios.
 *
 * <p>Vive en MongoDB por su naturaleza documental: la respuesta del proveedor y los reportes de moderación se
 * guardan embebidos en el mismo documento.
 */
public final class Review {

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;
    private static final int MAX_COMMENT_LENGTH = 2000;

    private final String id;
    private final UUID touristId;
    private final ReviewTargetType targetType;
    private final UUID targetId;
    private final int rating;
    private final String comment;
    private final Instant createdAt;
    private final List<ReviewReport> reports;
    private ReviewResponse response;
    private Instant updatedAt;
    private ReviewModerationStatus moderationStatus;
    private UUID moderatedBy;
    private Instant moderatedAt;
    private String moderationReason;

    private Review(
            String id,
            UUID touristId,
            ReviewTargetType targetType,
            UUID targetId,
            int rating,
            String comment,
            Instant createdAt,
            Instant updatedAt,
            ReviewResponse response,
            ReviewModerationStatus moderationStatus,
            List<ReviewReport> reports,
            UUID moderatedBy,
            Instant moderatedAt,
            String moderationReason) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("El identificador de la reseña es obligatorio");
        }
        this.touristId = Objects.requireNonNull(touristId, "El turista es obligatorio");
        this.targetType = Objects.requireNonNull(targetType, "El tipo de destino es obligatorio");
        this.targetId = Objects.requireNonNull(targetId, "El destino es obligatorio");
        validateRating(rating);
        this.rating = rating;
        this.comment = normalizeComment(comment);
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creación es obligatoria");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualización es obligatoria");
        this.response = response;
        this.moderationStatus = Objects.requireNonNull(moderationStatus, "El estado de moderación es obligatorio");
        this.reports = new ArrayList<>(reports == null ? List.of() : reports);
        this.moderatedBy = moderatedBy;
        this.moderatedAt = moderatedAt;
        this.moderationReason = normalize(moderationReason);
        this.id = id;
    }

    public static Review create(
            UUID touristId, ReviewTargetType targetType, UUID targetId, int rating, String comment, Instant now) {
        return new Review(
                UUID.randomUUID().toString(),
                touristId,
                targetType,
                targetId,
                rating,
                comment,
                now,
                now,
                null,
                ReviewModerationStatus.VISIBLE,
                List.of(),
                null,
                null,
                null);
    }

    public static Review rehydrate(
            String id,
            UUID touristId,
            ReviewTargetType targetType,
            UUID targetId,
            int rating,
            String comment,
            Instant createdAt,
            Instant updatedAt,
            ReviewResponse response,
            ReviewModerationStatus moderationStatus,
            List<ReviewReport> reports,
            UUID moderatedBy,
            Instant moderatedAt,
            String moderationReason) {
        return new Review(
                id,
                touristId,
                targetType,
                targetId,
                rating,
                comment,
                createdAt,
                updatedAt,
                response,
                moderationStatus,
                reports,
                moderatedBy,
                moderatedAt,
                moderationReason);
    }

    public void respond(UUID responderId, String text, Instant now) {
        if (moderationStatus == ReviewModerationStatus.HIDDEN) {
            throw new ValidationException("No se puede responder una reseña oculta");
        }
        Instant responseCreatedAt = response == null ? now : response.createdAt();
        this.response = new ReviewResponse(responderId, text, responseCreatedAt, now);
        this.updatedAt = now;
    }

    public void report(UUID reporterId, String reason, Instant now) {
        if (moderationStatus == ReviewModerationStatus.HIDDEN) {
            throw new ValidationException("No se puede reportar una reseña oculta");
        }
        boolean alreadyReported =
                reports.stream().anyMatch(report -> report.reporterId().equals(reporterId));
        if (alreadyReported) {
            throw new ReviewAlreadyReportedException();
        }
        reports.add(new ReviewReport(reporterId, reason, now));
        this.updatedAt = now;
    }

    public void moderate(ModerationDecision decision, UUID adminId, String reason, Instant now) {
        this.moderationStatus =
                decision == ModerationDecision.HIDE ? ReviewModerationStatus.HIDDEN : ReviewModerationStatus.VISIBLE;
        this.moderatedBy = adminId;
        this.moderatedAt = now;
        this.moderationReason = normalize(reason);
        this.reports.clear();
        this.updatedAt = now;
    }

    public boolean isVisible() {
        return moderationStatus == ReviewModerationStatus.VISIBLE;
    }

    public boolean isReported() {
        return !reports.isEmpty();
    }

    private static void validateRating(int rating) {
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new ValidationException("La calificación debe estar entre 1 y 5 estrellas");
        }
    }

    private static String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        String normalized = comment.trim();
        if (normalized.length() > MAX_COMMENT_LENGTH) {
            throw new ValidationException("El comentario no puede superar los 2000 caracteres");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String id() {
        return id;
    }

    public UUID touristId() {
        return touristId;
    }

    public ReviewTargetType targetType() {
        return targetType;
    }

    public UUID targetId() {
        return targetId;
    }

    public int rating() {
        return rating;
    }

    public String comment() {
        return comment;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public ReviewResponse response() {
        return response;
    }

    public ReviewModerationStatus moderationStatus() {
        return moderationStatus;
    }

    public List<ReviewReport> reports() {
        return List.copyOf(reports);
    }

    public UUID moderatedBy() {
        return moderatedBy;
    }

    public Instant moderatedAt() {
        return moderatedAt;
    }

    public String moderationReason() {
        return moderationReason;
    }
}
