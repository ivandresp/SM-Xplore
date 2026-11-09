package com.smxplore.securityreports.infrastructure.adapters.in.web.error;

import com.smxplore.securityreports.domain.exceptions.ReportNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MS02-14: los errores de infraestructura/parseo se responden con un mensaje generico y estable.
 */
class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/security-reports").build());

    @Test
    void hidesTheInternalMessageOfAnIllegalArgumentException() {
        var response = handler.handleIllegalArgument(
                new IllegalArgumentException("Cannot invoke \"String.length()\" because \"value\" is null"), exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("Invalid request parameter.");
    }

    @Test
    void hidesTheSpringDetailOfAMalformedRequest() {
        var response = handler.handleServerWebInput(
                new ServerWebInputException("JSON parse error: Unexpected character ('}' (code 125))"), exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("Malformed request.");
    }

    @Test
    void keepsThePublicMessageOfADomainNotFoundError() {
        var reportId = UUID.randomUUID();

        var response = handler.handleReportNotFound(new ReportNotFoundException(reportId), exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).contains(reportId.toString());
    }
}
