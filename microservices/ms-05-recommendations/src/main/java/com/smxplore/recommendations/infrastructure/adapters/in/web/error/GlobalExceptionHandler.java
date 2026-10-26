package com.smxplore.recommendations.infrastructure.adapters.in.web.error;

import com.smxplore.recommendations.domain.exception.CategoryNotFoundException;
import com.smxplore.recommendations.domain.exception.ForbiddenOperationException;
import com.smxplore.recommendations.domain.exception.InvalidJwtException;
import com.smxplore.recommendations.domain.exception.InvalidRequestException;
import com.smxplore.recommendations.domain.exception.ItemNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

import java.net.URI;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({CategoryNotFoundException.class, ItemNotFoundException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(RuntimeException exception, ServerWebExchange exchange) {
        return error(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage(), exchange);
    }

    @ExceptionHandler(InvalidJwtException.class)
    public ResponseEntity<ProblemDetail> handleInvalidJwt(InvalidJwtException exception, ServerWebExchange exchange) {
        return error(HttpStatus.UNAUTHORIZED, "Invalid token", exception.getMessage(), exchange);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(ForbiddenOperationException exception,
                                                         ServerWebExchange exchange) {
        return error(HttpStatus.FORBIDDEN, "Forbidden", exception.getMessage(), exchange);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRequest(InvalidRequestException exception,
                                                              ServerWebExchange exchange) {
        return error(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage(), exchange);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ProblemDetail> handleValidation(WebExchangeBindException exception,
                                                          ServerWebExchange exchange) {
        String detail = Stream.concat(
                        exception.getBindingResult().getFieldErrors().stream()
                                .map(error -> error.getField() + ": "
                                        + (error.getDefaultMessage() == null ? "invalid value" : error.getDefaultMessage())),
                        exception.getBindingResult().getGlobalErrors().stream()
                                .map(error -> error.getDefaultMessage() == null ? "invalid value" : error.getDefaultMessage()))
                .distinct()
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "Invalid request",
                detail.isBlank() ? "Request validation failed." : detail, exchange);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException exception,
                                                                   ServerWebExchange exchange) {
        String detail = exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "Invalid request",
                detail.isBlank() ? "Request validation failed." : detail, exchange);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                            ServerWebExchange exchange) {
        return error(HttpStatus.BAD_REQUEST, "Invalid parameter",
                "Invalid value for parameter '" + exception.getName() + "'.", exchange);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ProblemDetail> handleServerWebInput(ServerWebInputException exception,
                                                              ServerWebExchange exchange) {
        String detail = exception.getReason() == null ? "Malformed request." : exception.getReason();
        return error(HttpStatus.BAD_REQUEST, "Invalid request", detail, exchange);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException exception,
                                                              ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String detail = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        return error(status, status.getReasonPhrase(), detail, exchange);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException exception,
                                                               ServerWebExchange exchange) {
        String detail = exception.getMessage() == null ? "Invalid argument." : exception.getMessage();
        return error(HttpStatus.BAD_REQUEST, "Invalid request", detail, exchange);
    }

    @ExceptionHandler({DataAccessException.class, Neo4jException.class})
    public ResponseEntity<ProblemDetail> handleGraphUnavailable(Exception exception, ServerWebExchange exchange) {
        log.error("Graph access error while processing {}", exchange.getRequest().getPath().value(), exception);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Recommendations temporarily unavailable",
                "The recommendation graph is temporarily unavailable. Please try again later.", exchange);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, ServerWebExchange exchange) {
        log.error("Unhandled error while processing {}", exchange.getRequest().getPath().value(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
                "An unexpected error occurred. Please try again later.", exchange);
    }

    private static ResponseEntity<ProblemDetail> error(HttpStatus status, String title, String detail,
                                                       ServerWebExchange exchange) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
