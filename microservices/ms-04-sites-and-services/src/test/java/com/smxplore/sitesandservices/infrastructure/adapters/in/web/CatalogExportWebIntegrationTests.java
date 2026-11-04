package com.smxplore.sitesandservices.infrastructure.adapters.in.web;

import com.smxplore.sitesandservices.AbstractMongoPostgresIntegrationTest;
import com.smxplore.sitesandservices.TestJwtSupport;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.catalog.CatalogExportItemResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.catalog.CatalogExportPageResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.category.CategoryResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.service.ServiceResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.site.SiteResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.zone.ZoneResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWebTestClient
class CatalogExportWebIntegrationTests extends AbstractMongoPostgresIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void exportsCatalogPagesWithTheEventPayload() {
        UUID managerId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        CategoryResponse category = createCategory(managerId);
        ZoneResponse zone = createZone(managerId);
        SiteResponse site = createSite(managerId, category.categoryId(), zone.zoneId());
        ServiceResponse service = createService(providerId, category.categoryId(), zone.zoneId(), site.siteId());

        CatalogExportPageResponse sites = export(adminId, "SITES", null, 0, 50);
        CatalogExportItemResponse exportedSite = itemWithId(sites, site.siteId());
        assertThat(exportedSite.eventType()).isEqualTo("catalog.site.changed");
        assertThat(exportedSite.payload())
                .containsEntry("siteId", site.siteId().toString())
                .containsEntry("name", site.name())
                .containsEntry("categoryId", category.categoryId().toString())
                .containsEntry("status", "PUBLISHED")
                .containsKeys("latitude", "longitude", "updatedAt");

        CatalogExportPageResponse services = export(adminId, "SERVICES", null, 0, 50);
        CatalogExportItemResponse exportedService = itemWithId(services, service.serviceId());
        assertThat(exportedService.eventType()).isEqualTo("catalog.service.changed");
        assertThat(exportedService.payload())
                .containsEntry("serviceId", service.serviceId().toString())
                .containsEntry("siteId", site.siteId().toString())
                .containsEntry("type", "RESTAURANT")
                .containsKeys("latitude", "longitude", "status", "updatedAt");

        CatalogExportPageResponse categories = export(adminId, "CATEGORIES", null, 0, 50);
        CatalogExportItemResponse exportedCategory = itemWithId(categories, category.categoryId());
        assertThat(exportedCategory.eventType()).isEqualTo("catalog.category.changed");
        assertThat(exportedCategory.payload())
                .containsEntry("categoryId", category.categoryId().toString())
                .containsEntry("scope", "GENERAL")
                .containsEntry("parentId", null);

        CatalogExportPageResponse incremental = export(adminId, "SITES", Instant.now().plusSeconds(60), 0, 50);
        assertThat(incremental.content()).isEmpty();
        assertThat(incremental.total()).isZero();
        assertThat(incremental.updatedSince()).isNotNull();
    }

    private CatalogExportItemResponse itemWithId(CatalogExportPageResponse page, UUID aggregateId) {
        return page.content().stream()
                .filter(item -> aggregateId.equals(item.aggregateId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("catalog export did not contain " + aggregateId));
    }

    @Test
    void onlyAdministratorsCanExportTheCatalog() {
        UUID managerId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();

        clientFor(managerId, "DESTINATION_MANAGER")
                .get().uri("/api/v1/admin/catalog/export?entity=SITES")
                .exchange()
                .expectStatus().isForbidden();

        clientFor(providerId, "SERVICE_PROVIDER")
                .get().uri("/api/v1/admin/catalog/export?entity=SITES")
                .exchange()
                .expectStatus().isForbidden();

        clientFor(managerId, "ADMIN")
                .get().uri("/api/v1/admin/catalog/export?entity=SITES")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void rejectsInvalidExportParameters() {
        UUID adminId = UUID.randomUUID();

        clientFor(adminId, "ADMIN")
                .get().uri("/api/v1/admin/catalog/export")
                .exchange()
                .expectStatus().isBadRequest();

        clientFor(adminId, "ADMIN")
                .get().uri("/api/v1/admin/catalog/export?entity=SITES&size=500")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("size must be between 1 and 200");

        clientFor(adminId, "ADMIN")
                .get().uri("/api/v1/admin/catalog/export?entity=SITES&page=-1")
                .exchange()
                .expectStatus().isBadRequest();
    }

    private CatalogExportPageResponse export(
            UUID userId,
            String entity,
            Instant updatedSince,
            int page,
            int size
    ) {
        String uri = "/api/v1/admin/catalog/export?entity=" + entity + "&page=" + page + "&size=" + size;
        if (updatedSince != null) {
            uri = uri + "&updatedSince=" + updatedSince.toString();
        }
        return clientFor(userId, "ADMIN")
                .get().uri(uri)
                .exchange()
                .expectStatus().isOk()
                .expectBody(CatalogExportPageResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private CategoryResponse createCategory(UUID managerId) {
        return clientFor(managerId, "DESTINATION_MANAGER")
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
                          "photosUrls":[],
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

    private WebTestClient clientFor(UUID userId, String role) {
        return webTestClient.mutate()
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Bearer " + TestJwtSupport.bearer(jwtEncoder, userId, role, "Test User"))
                .build();
    }
}
