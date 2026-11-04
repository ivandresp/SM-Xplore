package com.smxplore.securityreports.infrastructure.adapters.out.persistence;

import com.smxplore.securityreports.TestcontainersConfiguration;
import com.smxplore.securityreports.domain.model.Comment;
import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import com.smxplore.securityreports.domain.model.types.Location;
import com.smxplore.securityreports.domain.model.types.PageRequest;
import com.smxplore.securityreports.domain.model.types.UserRef;
import com.smxplore.securityreports.domain.ports.repository.CommentRepositoryPort;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.CommentDocument;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.UserRefDocument;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.repository.CommentMongoRepository;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.repository.SecurityReportMongoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PersistenceAdaptersIntegrationTests {

    @Autowired
    private SecurityReportRepositoryPort securityReportRepository;

    @Autowired
    private CommentRepositoryPort commentRepository;

    @Autowired
    private SecurityReportMongoRepository securityReportMongoRepository;

    @Autowired
    private CommentMongoRepository commentMongoRepository;

    @Autowired
    private ReactiveMongoOperations mongoOperations;

    @AfterEach
    void cleanDatabase() {
        securityReportMongoRepository.deleteAll().block();
        commentMongoRepository.deleteAll().block();
    }

    @Test
    void savesAndReadsSecurityReport() {
        var user = new UserRef(UUID.randomUUID(), "Nestor");
        var report = SecurityReport.builder()
                .user(user)
                .title("Robo en la playa")
                .description("Un grupo de personas robó a un turista.")
                .location(new Location(11.2408, -74.1990))
                .photosUrls(new ArrayList<>(List.of("https://example.com/photo.jpg")))
                .build();
        report.validate();

        var saved = securityReportRepository.save(report).block();
        var found = securityReportRepository.findById(report.getId()).block();

        assertThat(saved).isNotNull();
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(report.getId());
        assertThat(found.getUser()).isEqualTo(user);
        assertThat(found.getTitle()).isEqualTo("Robo en la playa");
        assertThat(found.getDescription()).isEqualTo("Un grupo de personas robó a un turista.");
        assertThat(found.getLocation()).isEqualTo(new Location(11.2408, -74.1990));
        assertThat(found.getPhotosUrls()).containsExactly("https://example.com/photo.jpg");
        assertThat(found.getStatus()).isEqualTo(SecurityReportStatus.ONGOING);
        assertThat(found.getCreatedAt()).isEqualTo(report.getCreatedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
    }

    @Test
    void findsReportsByStatusAndUserAndUpdatesContentAndStatus() {
        var firstUser = new UserRef(UUID.randomUUID(), "Nestor");
        var secondUser = new UserRef(UUID.randomUUID(), "Ivan");
        var firstReport = reportFor(firstUser, "Primer reporte");
        var secondReport = reportFor(secondUser, "Segundo reporte");
        securityReportRepository.save(firstReport).block();
        securityReportRepository.save(secondReport).block();

        assertThat(securityReportRepository.findAllByStatus(SecurityReportStatus.ONGOING, PageRequest.first(10))
                .block().content()).hasSize(2);
        assertThat(securityReportRepository.findAllByUserId(firstUser.id(), PageRequest.first(10))
                .block().content())
                .extracting(SecurityReport::getId)
                .containsExactly(firstReport.getId());

        var reportToUpdate = securityReportRepository.findById(firstReport.getId()).block();
        reportToUpdate.changeContent("Título actualizado", "Descripción actualizada");
        reportToUpdate.markAsReported();
        securityReportRepository.save(reportToUpdate).block();

        var updated = securityReportRepository.findById(firstReport.getId()).block();
        assertThat(updated).isNotNull();
        assertThat(updated.getTitle()).isEqualTo("Título actualizado");
        assertThat(updated.getDescription()).isEqualTo("Descripción actualizada");
        assertThat(updated.getStatus()).isEqualTo(SecurityReportStatus.REPORTED);
    }

    @Test
    void savesAndFindsCommentsByReportIdOrderedByCreationDate() {
        var reportId = UUID.randomUUID();
        var user = new UserRef(UUID.randomUUID(), "Nestor");
        var firstComment = Comment.builder()
                .reportId(reportId)
                .user(user)
                .content("Primero en el tiempo.")
                .createdAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();
        var secondComment = Comment.builder()
                .reportId(reportId)
                .user(user)
                .content("Segundo en el tiempo.")
                .createdAt(Instant.parse("2026-01-02T10:00:00Z"))
                .build();
        firstComment.validate();
        secondComment.validate();

        // Save the newest first to prove the adapter sorts by createdAt, not insertion order.
        commentRepository.save(secondComment).block();
        var saved = commentRepository.save(firstComment).block();
        var comments = commentRepository.findAllByReportId(reportId, PageRequest.first(10)).block().content();

        assertThat(saved).isNotNull();
        assertThat(comments).hasSize(2);
        assertThat(comments).extracting(Comment::getContent)
                .containsExactly("Primero en el tiempo.", "Segundo en el tiempo.");
        assertThat(comments.getFirst().getId()).isEqualTo(firstComment.getId());
        assertThat(comments.getFirst().getReportId()).isEqualTo(reportId);
        assertThat(comments.getFirst().getUser()).isEqualTo(user);
        assertThat(comments.getFirst().getCreatedAt()).isEqualTo(firstComment.getCreatedAt());
        assertThat(commentRepository.findAllByReportId(UUID.randomUUID(), PageRequest.first(10))
                .block().content()).isEmpty();
    }

    @Test
    void paginatesReportsWithCursor() {
        var user = new UserRef(UUID.randomUUID(), "Nestor");
        var first = reportWithDate(user, "Primero", "2026-01-01T10:00:00Z");
        var second = reportWithDate(user, "Segundo", "2026-01-02T10:00:00Z");
        var third = reportWithDate(user, "Tercero", "2026-01-03T10:00:00Z");
        securityReportRepository.save(first).block();
        securityReportRepository.save(second).block();
        securityReportRepository.save(third).block();

        var firstPage = securityReportRepository
                .findAllByStatus(SecurityReportStatus.ONGOING, PageRequest.first(2)).block();

        assertThat(firstPage).isNotNull();
        assertThat(firstPage.content()).extracting(SecurityReport::getTitle)
                .containsExactly("Tercero", "Segundo");
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.nextCursor()).isNotNull();

        var secondPage = securityReportRepository
                .findAllByStatus(SecurityReportStatus.ONGOING, new PageRequest(2, firstPage.nextCursor())).block();

        assertThat(secondPage).isNotNull();
        assertThat(secondPage.content()).extracting(SecurityReport::getTitle)
                .containsExactly("Primero");
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    void paginatesReportsWithTiedCreationDatesWithoutDuplicates() {
        var user = new UserRef(UUID.randomUUID(), "Nestor");
        var first = reportWithDate(user, "Primero", "2026-01-01T10:00:00Z");
        var second = reportWithDate(user, "Segundo", "2026-01-01T10:00:00Z");
        securityReportRepository.save(first).block();
        securityReportRepository.save(second).block();

        var firstPage = securityReportRepository
                .findAllByStatus(SecurityReportStatus.ONGOING, PageRequest.first(1)).block();
        var secondPage = securityReportRepository
                .findAllByStatus(SecurityReportStatus.ONGOING, new PageRequest(1, firstPage.nextCursor())).block();

        assertThat(firstPage).isNotNull();
        assertThat(secondPage).isNotNull();
        assertThat(firstPage.content()).hasSize(1);
        assertThat(secondPage.content()).hasSize(1);
        assertThat(firstPage.content().getFirst().getId())
                .isNotEqualTo(secondPage.content().getFirst().getId());
    }

    @Test
    void paginatesCommentsWithCursor() {
        var reportId = UUID.randomUUID();
        var user = new UserRef(UUID.randomUUID(), "Nestor");
        var first = commentWithDate(reportId, user, "Primero", "2026-01-01T10:00:00Z");
        var second = commentWithDate(reportId, user, "Segundo", "2026-01-02T10:00:00Z");
        var third = commentWithDate(reportId, user, "Tercero", "2026-01-03T10:00:00Z");
        commentRepository.save(first).block();
        commentRepository.save(second).block();
        commentRepository.save(third).block();

        var firstPage = commentRepository.findAllByReportId(reportId, PageRequest.first(2)).block();

        assertThat(firstPage).isNotNull();
        assertThat(firstPage.content()).extracting(Comment::getContent)
                .containsExactly("Primero", "Segundo");
        assertThat(firstPage.hasNext()).isTrue();

        var secondPage = commentRepository
                .findAllByReportId(reportId, new PageRequest(2, firstPage.nextCursor())).block();

        assertThat(secondPage).isNotNull();
        assertThat(secondPage.content()).extracting(Comment::getContent)
                .containsExactly("Tercero");
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    void rejectsStaleReportUpdates() {
        var report = reportFor(new UserRef(UUID.randomUUID(), "Nestor"), "Reporte concurrente");
        var saved = securityReportRepository.save(report).block();

        var firstCopy = securityReportRepository.findById(saved.getId()).block();
        var staleCopy = securityReportRepository.findById(saved.getId()).block();

        firstCopy.changeContent("Primera actualización", "Primera");
        securityReportRepository.save(firstCopy).block();

        staleCopy.changeContent("Segunda actualización", "Segunda");
        assertThatThrownBy(() -> securityReportRepository.save(staleCopy).block())
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void mapsLegacyCommentsWithoutCreatedAtToEpoch() {
        var reportId = UUID.randomUUID();
        var legacyComment = CommentDocument.builder()
                .id(UUID.randomUUID().toString())
                .reportId(reportId.toString())
                .user(UserRefDocument.builder()
                        .userId(UUID.randomUUID().toString())
                        .name("Legacy")
                        .build())
                .content("Comentario sin fecha")
                .build();
        mongoOperations.save(legacyComment).block();

        var comments = commentRepository.findAllByReportId(reportId, PageRequest.first(10)).block().content();

        assertThat(comments).hasSize(1);
        assertThat(comments.getFirst().getCreatedAt()).isEqualTo(Instant.EPOCH);
    }

    private static SecurityReport reportWithDate(UserRef user, String title, String createdAt) {
        var report = SecurityReport.builder()
                .user(user)
                .title(title)
                .description("Descripción de " + title)
                .createdAt(Instant.parse(createdAt))
                .build();
        report.validate();
        return report;
    }

    private static Comment commentWithDate(UUID reportId, UserRef user, String content, String createdAt) {
        var comment = Comment.builder()
                .reportId(reportId)
                .user(user)
                .content(content)
                .createdAt(Instant.parse(createdAt))
                .build();
        comment.validate();
        return comment;
    }

    private static SecurityReport reportFor(UserRef user, String title) {
        var report = SecurityReport.builder()
                .user(user)
                .title(title)
                .description("Descripción de " + title)
                .build();
        report.validate();
        return report;
    }
}
