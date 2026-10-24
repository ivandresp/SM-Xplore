package com.smxplore.securityreports.infrastructure.adapters.out.persistence;

import com.smxplore.securityreports.TestcontainersConfiguration;
import com.smxplore.securityreports.domain.model.Comment;
import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import com.smxplore.securityreports.domain.model.types.Location;
import com.smxplore.securityreports.domain.model.types.UserRef;
import com.smxplore.securityreports.domain.ports.repository.CommentRepositoryPort;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.repository.CommentMongoRepository;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.repository.SecurityReportMongoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(securityReportRepository.findAllByStatus(SecurityReportStatus.ONGOING)
                .collectList().block()).hasSize(2);
        assertThat(securityReportRepository.findAllByUserId(firstUser.id())
                .collectList().block())
                .extracting(SecurityReport::getId)
                .containsExactly(firstReport.getId());

        securityReportRepository.changeContent(firstReport.getId(), "Título actualizado", "Descripción actualizada").block();
        securityReportRepository.changeStatus(firstReport.getId(), SecurityReportStatus.REPORTED).block();

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
        var comments = commentRepository.findAllByReportId(reportId).collectList().block();

        assertThat(saved).isNotNull();
        assertThat(comments).hasSize(2);
        assertThat(comments).extracting(Comment::getContent)
                .containsExactly("Primero en el tiempo.", "Segundo en el tiempo.");
        assertThat(comments.getFirst().getId()).isEqualTo(firstComment.getId());
        assertThat(comments.getFirst().getReportId()).isEqualTo(reportId);
        assertThat(comments.getFirst().getUser()).isEqualTo(user);
        assertThat(comments.getFirst().getCreatedAt()).isEqualTo(firstComment.getCreatedAt());
        assertThat(commentRepository.findAllByReportId(UUID.randomUUID()).collectList().block()).isEmpty();
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
