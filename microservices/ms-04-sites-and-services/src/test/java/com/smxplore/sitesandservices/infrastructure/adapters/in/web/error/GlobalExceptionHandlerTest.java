package com.smxplore.sitesandservices.infrastructure.adapters.in.web.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MS04-13: el 400 de {@link IllegalArgumentException} no debe filtrar mensajes internos.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void illegalArgumentsDoNotLeakInternalDetails() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/services").build());

        ResponseEntity<ProblemDetail> response = handler.handleIllegalArgument(
                new IllegalArgumentException("Cannot convert Decimal128 123.45 to BigDecimal"),
                exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Invalid request");
        assertThat(response.getBody().getDetail())
                .isEqualTo("The request contains an invalid value.")
                .doesNotContain("Decimal128")
                .doesNotContain("BigDecimal");
    }

    @Test
    void illegalArgumentsWithoutMessageStillReturnAGenericDetail() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/services").build());

        ResponseEntity<ProblemDetail> response = handler.handleIllegalArgument(
                new IllegalArgumentException(), exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("The request contains an invalid value.");
    }
}
