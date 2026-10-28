package com.smxplore.sitesandservices.infrastructure.adapters.in.web;

import com.smxplore.sitesandservices.AbstractMongoPostgresIntegrationTest;
import com.smxplore.sitesandservices.TestJwtSupport;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.category.CategoryResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.PageResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.ReviewResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.ReviewSummaryResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.service.ServiceResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.site.SiteResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.zone.ZoneResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.OutboxEventDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWebTestClient
class CatalogWebIntegrationTests extends AbstractMongoPostgresIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    @Test
    void managesSitesServicesAndReviewsEndToEnd() {
        UUID managerId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID touristId = UUID.randomUUID();

        CategoryResponse category = createCategory(managerId);
        ZoneResponse zone = createZone(managerId);
        SiteResponse site = createSite(managerId, category.categoryId(), zone.zoneId());
        ServiceResponse service = createService(
                providerId, category.categoryId(), zone.zoneId(), site.siteId());
        ReviewResponse review = createReview(touristId, site.siteId());
        respondToReview(managerId, review.reviewId());

        ReviewSummaryResponse summary = clientFor(touristId, "TURISTA")
                .get().uri("/api/v1/reviews/summary?itemType=SITE&itemId=" + site.siteId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ReviewSummaryResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(summary).isNotNull();
        assertThat(summary.reviewCount()).isEqualTo(1);
        assertThat(summary.averageRating()).isEqualTo(5.0);

        PageResponse<CategoryResponse> categories = listCategories(touristId);
        assertThat(categories.content())
                .extracting(CategoryResponse::categoryId)
                .contains(category.categoryId());

        PageResponse<ZoneResponse> zones = listZones(touristId);
        assertThat(zones.content())
                .extracting(ZoneResponse::zoneId)
                .contains(zone.zoneId());

        PageResponse<SiteResponse> sites = listSites(touristId, zone.zoneId());
        assertThat(sites.total()).isEqualTo(1);
        assertThat(sites.content().getFirst().siteId()).isEqualTo(site.siteId());

        PageResponse<ServiceResponse> services = listServices(touristId, site.siteId());
        assertThat(services.total()).isEqualTo(1);
        assertThat(services.content().getFirst().serviceId()).isEqualTo(service.serviceId());

        PageResponse<ReviewResponse> reviews = listReviews(touristId, site.siteId());
        assertThat(reviews.total()).isEqualTo(1);
        assertThat(reviews.content().getFirst().response()).isNotBlank();

        List<String> aggregateIds = List.of(
                category.categoryId().toString(),
                site.siteId().toString(),
                service.serviceId().toString(),
                review.reviewId().toString()
        );
        List<OutboxEventDocument> pending = mongoTemplate.find(
                        Query.query(Criteria.where("status").is("PENDING")
                                .and("aggregateId").in(aggregateIds)),
                        OutboxEventDocument.class)
                .collectList()
                .block();
        assertThat(pending)
                .isNotNull()
                .extracting(OutboxEventDocument::getEventType)
                .contains(
                        "catalog.category.changed",
                        "catalog.site.changed",
                        "catalog.service.changed",
                        "reviews.review.changed"
                );
    }

    @Test
    void duplicateReviewIsRejected() {
        UUID managerId = UUID.randomUUID();
        UUID touristId = UUID.randomUUID();

        CategoryResponse category = createCategory(managerId);
        ZoneResponse zone = createZone(managerId);
        SiteResponse site = createSite(managerId, category.categoryId(), zone.zoneId());
        createReview(touristId, site.siteId());

        clientFor(touristId, "TURISTA")
                .post().uri("/api/v1/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reviewBody(site.siteId()))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void touristCannotCreateCatalogResourcesAndAnonymousCannotRead() {
        UUID touristId = UUID.randomUUID();

        clientFor(touristId, "TURISTA")
                .post().uri("/api/v1/sites")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden();

        webTestClient.get().uri("/api/v1/sites")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
        webTestClient.get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk();
    }

    private CategoryResponse createCategory(UUID managerId) {
        return clientFor(managerId, "GESTORA_DESTINOS")
                .post().uri("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Naturaleza","scope":"GENERAL","parentId":null}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CategoryResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private ZoneResponse createZone(UUID managerId) {
        return clientFor(managerId, "GESTORA_DESTINOS")
                .post().uri("/api/v1/zones")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Centro","type":"URBAN","description":"Zona céntrica","status":"ACTIVE"}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ZoneResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private SiteResponse createSite(UUID managerId, UUID categoryId, UUID zoneId) {
        return clientFor(managerId, "GESTORA_DESTINOS")
                .post().uri("/api/v1/sites")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name":"Playa de prueba",
                          "description":"Un sitio de prueba",
                          "categoryId":"%s",
                          "zoneId":"%s",
                          "latitude":10.39,
                          "longitude":-75.47,
                          "photosUrls":["https://example.com/site.jpg"],
                          "maxCapacity":100,
                          "availability":[{"dayOfWeek":"MONDAY","opensAt":"09:00:00","closesAt":"17:00:00"}],
                          "status":"PUBLISHED"
                        }
                        """.formatted(categoryId, zoneId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(SiteResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private ServiceResponse createService(
            UUID providerId,
            UUID categoryId,
            UUID zoneId,
            UUID siteId
    ) {
        return clientFor(providerId, "PRESTADORA_SERVICIOS")
                .post().uri("/api/v1/services")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name":"Restaurante de prueba",
                          "description":"Servicio de prueba",
                          "type":"RESTAURANT",
                          "categoryId":"%s",
                          "siteId":"%s",
                          "zoneId":"%s",
                          "latitude":10.39,
                          "longitude":-75.47,
                          "price":50000,
                          "maxCapacity":20,
                          "availability":[{"dayOfWeek":"MONDAY","opensAt":"10:00:00","closesAt":"18:00:00"}],
                          "photosUrls":[],
                          "cancellationPolicy":"48 horas",
                          "status":"PUBLISHED"
                        }
                        """.formatted(categoryId, siteId, zoneId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ServiceResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private ReviewResponse createReview(UUID touristId, UUID siteId) {
        return clientFor(touristId, "TURISTA")
                .post().uri("/api/v1/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reviewBody(siteId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ReviewResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private String reviewBody(UUID siteId) {
        return """
                {
                  "itemType":"SITE",
                  "itemId":"%s",
                  "rating":5,
                  "commentary":"Excelente lugar",
                  "photosUrls":[]
                }
                """.formatted(siteId);
    }

    private void respondToReview(UUID managerId, UUID reviewId) {
        clientFor(managerId, "GESTORA_DESTINOS")
                .post().uri("/api/v1/reviews/" + reviewId + "/response")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"response":"Gracias por tu visita"}
                        """)
                .exchange()
                .expectStatus().isOk();
    }

    private PageResponse<CategoryResponse> listCategories(UUID touristId) {
        return clientFor(touristId, "TURISTA")
                .get().uri("/api/v1/categories")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<PageResponse<CategoryResponse>>() {
                })
                .returnResult()
                .getResponseBody();
    }

    private PageResponse<ZoneResponse> listZones(UUID touristId) {
        return clientFor(touristId, "TURISTA")
                .get().uri("/api/v1/zones")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<PageResponse<ZoneResponse>>() {
                })
                .returnResult()
                .getResponseBody();
    }

    private PageResponse<SiteResponse> listSites(UUID touristId, UUID zoneId) {
        return clientFor(touristId, "TURISTA")
                .get().uri("/api/v1/sites?zoneId=" + zoneId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<PageResponse<SiteResponse>>() {
                })
                .returnResult()
                .getResponseBody();
    }

    private PageResponse<ServiceResponse> listServices(UUID touristId, UUID siteId) {
        return clientFor(touristId, "TURISTA")
                .get().uri("/api/v1/services?siteId=" + siteId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<PageResponse<ServiceResponse>>() {
                })
                .returnResult()
                .getResponseBody();
    }

    private PageResponse<ReviewResponse> listReviews(UUID touristId, UUID siteId) {
        return clientFor(touristId, "TURISTA")
                .get().uri("/api/v1/reviews?itemType=SITE&itemId=" + siteId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<PageResponse<ReviewResponse>>() {
                })
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
