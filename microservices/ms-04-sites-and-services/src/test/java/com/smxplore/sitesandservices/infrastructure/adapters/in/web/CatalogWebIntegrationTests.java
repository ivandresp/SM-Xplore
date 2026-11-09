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

        ReviewSummaryResponse summary = clientFor(touristId, "TOURIST")
                .get().uri("/api/v1/reviews/summary?itemType=SITE&itemId=" + site.siteId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ReviewSummaryResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(summary).isNotNull();
        assertThat(summary.reviewCount()).isEqualTo(1);
        assertThat(summary.averageRating()).isEqualTo(5.0);

        PageResponse<CategoryResponse> categories = listCategories(touristId, category.name());
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

        clientFor(touristId, "TOURIST")
                .post().uri("/api/v1/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reviewBody(site.siteId()))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void touristCannotCreateCatalogResourcesAndAnonymousCanReadCatalog() {
        UUID touristId = UUID.randomUUID();

        clientFor(touristId, "TOURIST")
                .post().uri("/api/v1/sites")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden();

        // El catalogo es lectura publica: el turista lo consulta antes de registrarse.
        webTestClient.get().uri("/api/v1/sites")
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/api/v1/zones")
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/api/v1/categories")
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/api/v1/services")
                .exchange()
                .expectStatus().isOk();

        // Filtrar por gestora exige saber quien pregunta: un anonimo recibe 403, no 500.
        webTestClient.get().uri("/api/v1/sites?managerId=" + UUID.randomUUID())
                .exchange()
                .expectStatus().isForbidden();

        // Lo que no es catalogo sigue exigiendo sesion.
        webTestClient.get().uri("/api/v1/notifications")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.get().uri("/api/v1/reservations")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.get().uri("/api/v1/admin/catalog/export")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
        // El scrape de metrics inicializa medidores y con la suite completa puede tardar mas que
        // el timeout por defecto del WebTestClient; se le da margen para no fallar por lentitud.
        webTestClient.mutate()
                .responseTimeout(java.time.Duration.ofSeconds(30))
                .build()
                .get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * Auditoria final 2026-11-08: el listado publico de resenas mostraba el touristId (un UUID
     * estable de la persona) a cualquiera. Ahora solo expone el nombre y la vista administrativa
     * conserva el identificador.
     */
    @Test
    void publicReviewListingShowsTheAuthorNameButNotTheIdentifier() {
        UUID managerId = UUID.randomUUID();
        UUID touristId = UUID.randomUUID();
        CategoryResponse category = createCategory(managerId);
        ZoneResponse zone = createZone(managerId);
        SiteResponse site = createSite(managerId, category.categoryId(), zone.zoneId());
        createReview(touristId, site.siteId());

        String json = webTestClient.get()
                .uri("/api/v1/reviews?itemType=SITE&itemId=" + site.siteId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(json).isNotNull();
        assertThat(json).contains("\"touristName\"");
        assertThat(json).doesNotContain("\"touristId\"");
    }

    @Test
    void filtersSitesByManagerForAdministrators() {
        UUID managerA = UUID.randomUUID();
        UUID managerB = UUID.randomUUID();
        SiteResponse siteA = createSiteFor(managerA);
        SiteResponse siteB = createSiteFor(managerB);

        PageResponse<SiteResponse> page = listSitesByManager(UUID.randomUUID(), "ADMIN", managerA);

        assertThat(page.content()).extracting(SiteResponse::siteId)
                .contains(siteA.siteId())
                .doesNotContain(siteB.siteId());
    }

    @Test
    void letsADestinationManagerListItsOwnSitesButNotAnotherManagers() {
        UUID managerA = UUID.randomUUID();
        UUID managerB = UUID.randomUUID();
        SiteResponse siteA = createSiteFor(managerA);

        PageResponse<SiteResponse> own = listSitesByManager(managerA, "DESTINATION_MANAGER", managerA);
        assertThat(own.content()).extracting(SiteResponse::siteId).contains(siteA.siteId());

        clientFor(managerA, "DESTINATION_MANAGER")
                .get().uri("/api/v1/sites?managerId=" + managerB)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void rejectsTheManagerFilterForTouristsAndProviders() {
        UUID managerId = UUID.randomUUID();

        clientFor(UUID.randomUUID(), "TOURIST")
                .get().uri("/api/v1/sites?managerId=" + managerId)
                .exchange()
                .expectStatus().isForbidden();

        clientFor(UUID.randomUUID(), "SERVICE_PROVIDER")
                .get().uri("/api/v1/sites?managerId=" + managerId)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void keepsTheListingUnfilteredWhenManagerIdIsMissing() {
        UUID managerId = UUID.randomUUID();
        SiteResponse site = createSiteFor(managerId);

        PageResponse<SiteResponse> page = clientFor(UUID.randomUUID(), "TOURIST")
                .get().uri("/api/v1/sites?categoryId=" + site.categoryId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<PageResponse<SiteResponse>>() {
                })
                .returnResult()
                .getResponseBody();

        assertThat(page.content()).extracting(SiteResponse::siteId).contains(site.siteId());
    }

    private SiteResponse createSiteFor(UUID managerId) {
        CategoryResponse category = createCategory(managerId);
        ZoneResponse zone = createZone(managerId);
        return createSite(managerId, category.categoryId(), zone.zoneId());
    }

    private PageResponse<SiteResponse> listSitesByManager(UUID callerId, String role, UUID managerId) {
        return clientFor(callerId, role)
                .get().uri("/api/v1/sites?managerId=" + managerId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<PageResponse<SiteResponse>>() {
                })
                .returnResult()
                .getResponseBody();
    }

    private CategoryResponse createCategory(UUID adminId) {
        String categoryName = "Naturaleza " + UUID.randomUUID();
        return clientFor(adminId, "ADMIN")
                .post().uri("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"%s","scope":"GENERAL","parentId":null}
                        """.formatted(categoryName))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CategoryResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private ZoneResponse createZone(UUID managerId) {
        return clientFor(managerId, "DESTINATION_MANAGER")
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
        return clientFor(managerId, "DESTINATION_MANAGER")
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

    @Test
    void refreshesTheServiceSnapshotWhenTheSiteIsRenamed() {
        UUID managerId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();

        CategoryResponse category = createCategory(managerId);
        ZoneResponse zone = createZone(managerId);
        SiteResponse site = createSite(managerId, category.categoryId(), zone.zoneId());
        ServiceResponse service = createService(
                providerId, category.categoryId(), zone.zoneId(), site.siteId());
        assertThat(service.siteName()).isEqualTo(site.name());

        SiteResponse renamed = renameSite(managerId, site, "Playa renombrada");
        assertThat(renamed.name()).isEqualTo("Playa renombrada");

        ServiceResponse reloaded = clientFor(managerId, "DESTINATION_MANAGER")
                .get().uri("/api/v1/services/" + service.serviceId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ServiceResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.siteName()).isEqualTo("Playa renombrada");
    }

    private SiteResponse renameSite(UUID managerId, SiteResponse site, String newName) {
        return clientFor(managerId, "DESTINATION_MANAGER")
                .put().uri("/api/v1/sites/" + site.siteId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name":"%s",
                          "description":"Un sitio de prueba",
                          "categoryId":"%s",
                          "zoneId":"%s",
                          "latitude":10.39,
                          "longitude":-75.47,
                          "photosUrls":["https://example.com/site.jpg"],
                          "maxCapacity":100,
                          "availability":[{"dayOfWeek":"MONDAY","opensAt":"09:00:00","closesAt":"17:00:00"}]
                        }
                        """.formatted(newName, site.categoryId(), site.zoneId()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(SiteResponse.class)
                .returnResult()
                .getResponseBody();
    }

    @Test
    void filtersServicesByPriceRange() {
        UUID managerId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID touristId = UUID.randomUUID();

        CategoryResponse category = createCategory(managerId);
        ZoneResponse zone = createZone(managerId);
        SiteResponse site = createSite(managerId, category.categoryId(), zone.zoneId());
        ServiceResponse cheap = createServiceWithPrice(
                providerId, category.categoryId(), zone.zoneId(), site.siteId(), "50000");
        ServiceResponse mid = createServiceWithPrice(
                providerId, category.categoryId(), zone.zoneId(), site.siteId(), "100000");
        ServiceResponse expensive = createServiceWithPrice(
                providerId, category.categoryId(), zone.zoneId(), site.siteId(), "200000");

        PageResponse<ServiceResponse> range = listServicesByPrice(
                touristId, site.siteId(), "60000", "150000");
        assertThat(range.total()).isEqualTo(1);
        assertThat(range.content()).extracting(ServiceResponse::serviceId)
                .containsExactly(mid.serviceId());

        PageResponse<ServiceResponse> minOnly = listServicesByPrice(
                touristId, site.siteId(), "60000", null);
        assertThat(minOnly.total()).isEqualTo(2);
        assertThat(minOnly.content()).extracting(ServiceResponse::serviceId)
                .containsExactlyInAnyOrder(mid.serviceId(), expensive.serviceId());

        PageResponse<ServiceResponse> maxOnly = listServicesByPrice(
                touristId, site.siteId(), null, "60000");
        assertThat(maxOnly.total()).isEqualTo(1);
        assertThat(maxOnly.content()).extracting(ServiceResponse::serviceId)
                .containsExactly(cheap.serviceId());

        PageResponse<ServiceResponse> noPrice = listServicesByPrice(
                touristId, site.siteId(), null, null);
        assertThat(noPrice.total()).isEqualTo(3);
    }

    private PageResponse<ServiceResponse> listServicesByPrice(
            UUID callerId, UUID siteId, String priceMin, String priceMax) {
        String uri = "/api/v1/services?siteId=" + siteId;
        if (priceMin != null) {
            uri += "&priceMin=" + priceMin;
        }
        if (priceMax != null) {
            uri += "&priceMax=" + priceMax;
        }
        return clientFor(callerId, "TOURIST")
                .get().uri(uri)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<PageResponse<ServiceResponse>>() {
                })
                .returnResult()
                .getResponseBody();
    }

    private ServiceResponse createService(
            UUID providerId,
            UUID categoryId,
            UUID zoneId,
            UUID siteId
    ) {
        return createServiceWithPrice(providerId, categoryId, zoneId, siteId, "50000");
    }

    private ServiceResponse createServiceWithPrice(
            UUID providerId,
            UUID categoryId,
            UUID zoneId,
            UUID siteId,
            String price
    ) {
        return clientFor(providerId, "SERVICE_PROVIDER")
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
                          "price":%s,
                          "maxCapacity":20,
                          "availability":[{"dayOfWeek":"MONDAY","opensAt":"10:00:00","closesAt":"18:00:00"}],
                          "photosUrls":[],
                          "cancellationPolicy":"48 horas",
                          "status":"PUBLISHED"
                        }
                        """.formatted(categoryId, siteId, zoneId, price))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ServiceResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private ReviewResponse createReview(UUID touristId, UUID siteId) {
        return clientFor(touristId, "TOURIST")
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
        clientFor(managerId, "DESTINATION_MANAGER")
                .post().uri("/api/v1/reviews/" + reviewId + "/response")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"response":"Gracias por tu visita"}
                        """)
                .exchange()
                .expectStatus().isOk();
    }

    private PageResponse<CategoryResponse> listCategories(UUID touristId, String search) {
        return clientFor(touristId, "TOURIST")
                .get().uri(uriBuilder -> uriBuilder
                        .path("/api/v1/categories")
                        .queryParam("search", search)
                        .queryParam("page", 0)
                        .queryParam("size", 100)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<PageResponse<CategoryResponse>>() {
                })
                .returnResult()
                .getResponseBody();
    }

    private PageResponse<ZoneResponse> listZones(UUID touristId) {
        return clientFor(touristId, "TOURIST")
                .get().uri("/api/v1/zones")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<PageResponse<ZoneResponse>>() {
                })
                .returnResult()
                .getResponseBody();
    }

    private PageResponse<SiteResponse> listSites(UUID touristId, UUID zoneId) {
        return clientFor(touristId, "TOURIST")
                .get().uri("/api/v1/sites?zoneId=" + zoneId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<PageResponse<SiteResponse>>() {
                })
                .returnResult()
                .getResponseBody();
    }

    private PageResponse<ServiceResponse> listServices(UUID touristId, UUID siteId) {
        return clientFor(touristId, "TOURIST")
                .get().uri("/api/v1/services?siteId=" + siteId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<PageResponse<ServiceResponse>>() {
                })
                .returnResult()
                .getResponseBody();
    }

    private PageResponse<ReviewResponse> listReviews(UUID touristId, UUID siteId) {
        return clientFor(touristId, "TOURIST")
                .get().uri("/api/v1/reviews?itemType=SITE&itemId=" + siteId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<PageResponse<ReviewResponse>>() {
                })
                .returnResult()
                .getResponseBody();
    }

    /**
     * MS04-01: la taxonomia global es solo de ADMIN (regla HTTP + defensa en profundidad)
     * y el arbol de categorias no admite ciclos ni padres inexistentes.
     */
    @Test
    void categoryTaxonomyIsAdminOnlyAndRejectsCycles() {
        UUID adminId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        clientFor(managerId, "DESTINATION_MANAGER")
                .post().uri("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Taxonomía gestora","scope":"GENERAL","parentId":null}
                        """)
                .exchange()
                .expectStatus().isForbidden();

        UUID parentId = createCategory(adminId).categoryId();
        UUID childId = createChildCategory(adminId, parentId);

        clientFor(adminId, "ADMIN")
                .put().uri("/api/v1/categories/" + childId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Naturaleza actualizada","scope":"GENERAL","parentId":"%s"}
                        """.formatted(parentId))
                .exchange()
                .expectStatus().isOk();

        clientFor(managerId, "DESTINATION_MANAGER")
                .put().uri("/api/v1/categories/" + childId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Intento de gestora","scope":"GENERAL","parentId":"%s"}
                        """.formatted(parentId))
                .exchange()
                .expectStatus().isForbidden();

        clientFor(adminId, "ADMIN")
                .put().uri("/api/v1/categories/" + parentId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Naturaleza","scope":"GENERAL","parentId":"%s"}
                        """.formatted(childId))
                .exchange()
                .expectStatus().isBadRequest();

        clientFor(adminId, "ADMIN")
                .post().uri("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Huérfana","scope":"GENERAL","parentId":"%s"}
                        """.formatted(UUID.randomUUID()))
                .exchange()
                .expectStatus().isBadRequest();
    }

    private UUID createChildCategory(UUID adminId, UUID parentId) {
        return clientFor(adminId, "ADMIN")
                .post().uri("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Playas","scope":"GENERAL","parentId":"%s"}
                        """.formatted(parentId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CategoryResponse.class)
                .returnResult()
                .getResponseBody()
                .categoryId();
    }

    private WebTestClient clientFor(UUID userId, String role) {
        return webTestClient.mutate()
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Bearer " + TestJwtSupport.bearer(jwtEncoder, userId, role, "Test User"))
                .build();
    }
}
