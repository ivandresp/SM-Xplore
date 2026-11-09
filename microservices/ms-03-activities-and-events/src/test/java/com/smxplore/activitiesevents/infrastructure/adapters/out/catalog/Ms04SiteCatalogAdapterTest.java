package com.smxplore.activitiesevents.infrastructure.adapters.out.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.activitiesevents.domain.exceptions.catalog.SiteCatalogUnavailableException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Regresion de MS03-15: la lectura paginada del catalogo de ms-04 tiene un tope. Una respuesta
 * anomala ({@code totalPages} enorme) falla con {@link SiteCatalogUnavailableException} en lugar de
 * encadenar peticiones sin fin.
 */
class Ms04SiteCatalogAdapterTest {

    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final UUID SITE_ID = UUID.randomUUID();
    private static final UUID ZONE_ID = UUID.randomUUID();

    @Test
    void capsTheNumberOfPagesReadFromTheCatalog() {
        var requests = new AtomicInteger();
        var adapter = new Ms04SiteCatalogAdapter(stubClient(requests, emptyPage(50)), Duration.ofSeconds(1), 2);

        StepVerifier.create(adapter.findByManagerId(MANAGER_ID, "token"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(SiteCatalogUnavailableException.class)
                        .hasMessageContaining("more than 2 pages"))
                .verify();

        assertThat(requests).hasValue(2);
    }

    @Test
    void treatsANonPositiveCapAsOnePage() {
        var requests = new AtomicInteger();
        var adapter = new Ms04SiteCatalogAdapter(stubClient(requests, emptyPage(50)), Duration.ofSeconds(1), 0);

        StepVerifier.create(adapter.findByZoneId(ZONE_ID, "token"))
                .expectError(SiteCatalogUnavailableException.class)
                .verify();

        assertThat(requests).hasValue(1);
    }

    @Test
    void readsTheCatalogWhenItFitsTheBudget() {
        var adapter = new Ms04SiteCatalogAdapter(stubClient(new AtomicInteger(), oneSitePage()),
                Duration.ofSeconds(1), 100);

        StepVerifier.create(adapter.findByManagerId(MANAGER_ID, "token"))
                .assertNext(site -> {
                    assertThat(site.siteId()).isEqualTo(SITE_ID);
                    assertThat(site.managerId()).isEqualTo(MANAGER_ID);
                    assertThat(site.name()).isEqualTo("Centro Historico");
                })
                .verifyComplete();
    }

    @Test
    void asksForTheFirstPageWithTheFixedPageSize() {
        var requestedUrl = new AtomicReference<String>();
        var webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    requestedUrl.set(request.url().toString());
                    return Mono.just(response(oneSitePage()));
                })
                .build();
        var adapter = new Ms04SiteCatalogAdapter(webClient, Duration.ofSeconds(1), 100);

        StepVerifier.create(adapter.findByManagerId(MANAGER_ID, "token")).expectNextCount(1).verifyComplete();

        assertThat(requestedUrl.get())
                .contains("/api/v1/sites")
                .contains("managerId=" + MANAGER_ID)
                .contains("page=0")
                .contains("size=100");
    }

    private static WebClient stubClient(AtomicInteger requests, String body) {
        return WebClient.builder()
                .exchangeFunction(request -> {
                    requests.incrementAndGet();
                    return Mono.just(response(body));
                })
                .build();
    }

    private static ClientResponse response(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static String emptyPage(int totalPages) {
        return "{\"content\":[],\"page\":0,\"size\":100,\"total\":0,\"totalPages\":" + totalPages + "}";
    }

    private static String oneSitePage() {
        return "{\"content\":[{\"siteId\":\"" + SITE_ID + "\",\"managerId\":\"" + MANAGER_ID
                + "\",\"name\":\"Centro Historico\",\"zoneId\":\"" + ZONE_ID
                + "\",\"zoneName\":\"Centro\",\"zoneStatus\":\"PUBLISHED\"}],"
                + "\"page\":0,\"size\":100,\"total\":1,\"totalPages\":1}";
    }
}
