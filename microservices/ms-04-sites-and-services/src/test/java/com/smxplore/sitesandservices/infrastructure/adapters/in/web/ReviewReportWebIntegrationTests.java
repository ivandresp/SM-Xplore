package com.smxplore.sitesandservices.infrastructure.adapters.in.web;

import com.smxplore.sitesandservices.AbstractMongoPostgresIntegrationTest;
import com.smxplore.sitesandservices.TestJwtSupport;
import com.smxplore.sitesandservices.domain.model.review.ReviewReportStatus;
import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.PageResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.ReputationResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.ReviewReportResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.ReviewResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWebTestClient
class ReviewReportWebIntegrationTests extends AbstractMongoPostgresIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void acceptsAReportAndHidesTheReview() {
        UUID authorId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID siteId = persistPublishedSite("Sitio reportes").siteId();
        ReviewResponse review = createReview(authorId, siteId, 1);

        ReviewReportResponse report = reportReview(reporterId, review.reviewId());

        assertThat(report.status()).isEqualTo(ReviewReportStatus.OPEN);
        assertThat(report.reportedBy()).isEqualTo(reporterId);
        assertThat(report.itemId()).isEqualTo(siteId);

        assertThat(listReports(adminId, ReviewReportStatus.OPEN).content())
                .extracting(ReviewReportResponse::reportId)
                .contains(report.reportId());

        ReviewReportResponse resolved = resolveReport(adminId, report.reportId(), "ACCEPTED");

        assertThat(resolved.status()).isEqualTo(ReviewReportStatus.ACCEPTED);
        assertThat(resolved.resolvedBy()).isEqualTo(adminId);

        ReviewResponse hidden = clientFor(authorId, "TOURIST")
                .get().uri("/api/v1/reviews/" + review.reviewId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ReviewResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(hidden.status()).isEqualTo(ReviewStatus.HIDDEN);

        ReputationResponse reputation = reputation(siteId);

        assertThat(reputation.reviewCount()).isZero();
        assertThat(reputation.averageRating()).isZero();
        assertThat(reputation.hiddenCount()).isEqualTo(1);
    }

    @Test
    void dismissesAReportAndKeepsTheReviewPublished() {
        UUID authorId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID siteId = persistPublishedSite("Sitio descartes").siteId();
        ReviewResponse review = createReview(authorId, siteId, 5);
        ReviewReportResponse report = reportReview(reporterId, review.reviewId());

        ReviewReportResponse dismissed = resolveReport(adminId, report.reportId(), "DISMISSED");

        assertThat(dismissed.status()).isEqualTo(ReviewReportStatus.DISMISSED);

        ReviewResponse stillPublished = clientFor(authorId, "TOURIST")
                .get().uri("/api/v1/reviews/" + review.reviewId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ReviewResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(stillPublished.status()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(reputation(siteId).reviewCount()).isEqualTo(1);

        clientFor(adminId, "ADMIN")
                .patch().uri("/api/v1/reviews/reports/" + report.reportId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"status":"ACCEPTED"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void rejectsDuplicateAndSelfReports() {
        UUID authorId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID siteId = persistPublishedSite("Sitio duplicados").siteId();
        ReviewResponse review = createReview(authorId, siteId, 4);

        reportReview(reporterId, review.reviewId());

        clientFor(reporterId, "TOURIST")
                .post().uri("/api/v1/reviews/" + review.reviewId() + "/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"reason":"SPAM"}
                        """)
                .exchange()
                .expectStatus().isEqualTo(409);

        clientFor(authorId, "TOURIST")
                .post().uri("/api/v1/reviews/" + review.reviewId() + "/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"reason":"OFFENSIVE"}
                        """)
                .exchange()
                .expectStatus().isForbidden();

        clientFor(reporterId, "TOURIST")
                .get().uri("/api/v1/reviews/reports")
                .exchange()
                .expectStatus().isForbidden();

        clientFor(adminId, "ADMIN")
                .get().uri("/api/v1/reviews/reports?page=0&size=200")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void aggregatesReputationMetrics() {
        UUID managerId = UUID.randomUUID();
        UUID firstTourist = UUID.randomUUID();
        UUID secondTourist = UUID.randomUUID();
        UUID siteId = persistPublishedSite("Sitio reputación").siteId();
        ReviewResponse fiveStars = createReview(firstTourist, siteId, 5);
        createReview(secondTourist, siteId, 3);

        clientFor(managerId, "ADMIN")
                .post().uri("/api/v1/reviews/" + fiveStars.reviewId() + "/response")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"response":"Gracias por tu visita"}
                        """)
                .exchange()
                .expectStatus().isOk();

        ReputationResponse reputation = reputation(siteId);

        assertThat(reputation.reviewCount()).isEqualTo(2);
        assertThat(reputation.averageRating()).isEqualTo(4.0);
        assertThat(reputation.responseCount()).isEqualTo(1);
        assertThat(reputation.responseRate()).isEqualTo(0.5);
        assertThat(reputation.hiddenCount()).isZero();
        assertThat(reputation.ratingDistribution())
                .containsEntry(3, 1L)
                .containsEntry(5, 1L)
                .containsEntry(1, 0L);
        assertThat(reputation.lastReviewAt()).isNotNull();
    }

    private ReviewResponse createReview(UUID touristId, UUID siteId, int rating) {
        return clientFor(touristId, "TOURIST")
                .post().uri("/api/v1/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "itemType":"SITE",
                          "itemId":"%s",
                          "rating":%d,
                          "commentary":"Comentario de prueba",
                          "photosUrls":[]
                        }
                        """.formatted(siteId, rating))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ReviewResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private ReviewReportResponse reportReview(UUID reporterId, UUID reviewId) {
        return clientFor(reporterId, "TOURIST")
                .post().uri("/api/v1/reviews/" + reviewId + "/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"reason":"SPAM","details":"Publicidad repetida"}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ReviewReportResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private ReviewReportResponse resolveReport(UUID adminId, UUID reportId, String status) {
        return clientFor(adminId, "ADMIN")
                .patch().uri("/api/v1/reviews/reports/" + reportId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"status":"%s","resolution":"Revisado por el equipo"}
                        """.formatted(status))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ReviewReportResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private PageResponse<ReviewReportResponse> listReports(
            UUID adminId,
            ReviewReportStatus status
    ) {
        return clientFor(adminId, "ADMIN")
                .get().uri("/api/v1/reviews/reports?status=" + status + "&page=0&size=20")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<PageResponse<ReviewReportResponse>>() {
                })
                .returnResult()
                .getResponseBody();
    }

    private ReputationResponse reputation(UUID siteId) {
        return clientFor(UUID.randomUUID(), "TOURIST")
                .get().uri("/api/v1/reviews/reputation?itemType=SITE&itemId=" + siteId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ReputationResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private WebTestClient clientFor(UUID userId, String role) {
        return webTestClient.mutate()
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Bearer " + TestJwtSupport.bearer(jwtEncoder, userId, role, "Test User"))
                .build();
    }
}
