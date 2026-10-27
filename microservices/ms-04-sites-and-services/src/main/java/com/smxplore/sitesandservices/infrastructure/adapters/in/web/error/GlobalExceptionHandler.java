package com.smxplore.sitesandservices.infrastructure.adapters.in.web.error;

import com.smxplore.sitesandservices.domain.exception.DomainStateException;
import com.smxplore.sitesandservices.domain.exception.DomainValidationException;
import com.smxplore.sitesandservices.domain.exception.DuplicateReviewException;
import com.smxplore.sitesandservices.domain.exception.ForbiddenOperationException;
import com.smxplore.sitesandservices.domain.exception.InvalidJwtException;
import com.smxplore.sitesandservices.domain.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(
            ResourceNotFoundException exception,
            ServerWebExchange exchange
    ) {
        return error(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage(), exchange);
    }

    @ExceptionHandler(InvalidJwtException.class)
    public ResponseEntity<ProblemDetail> handleInvalidJwt(
            InvalidJwtException exception,
            ServerWebExchange exchange
    ) {
        return error(HttpStatus.UNAUTHORIZED, "Invalid token", exception.getMessage(), exchange);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(
            ForbiddenOperationException exception,
            ServerWebExchange exchange
    ) {
        return error(HttpStatus.FORBIDDEN, "Forbidden", exception.getMessage(), exchange);
    }

    @ExceptionHandler(DuplicateReviewException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateReview(
            DuplicateReviewException exception,
            ServerWebExchange exchange
    ) {
        return error(HttpStatus.CONFLICT, "Duplicate review", exception.getMessage(), exchange);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateKey(
            DuplicateKeyException exception,
            ServerWebExchange exchange
    ) {
        return error(HttpStatus.CONFLICT, "Conflicting resource",
                "A resource with the same unique data already exists.", exchange);
    }

    @ExceptionHandler(DomainValidationException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            DomainValidationException exception,
            ServerWebExchange exchange
    ) {
        return error(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage(), exchange);
    }

    @ExceptionHandler(DomainStateException.class)
    public ResponseEntity<ProblemDetail> handleDomainState(
            DomainStateException exception,
            ServerWebExchange exchange
    ) {
        log.error("Domain state error while processing {}", exchange.getRequest().getPath().value(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
                "An internal consistency error occurred.", exchange);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ProblemDetail> handleRequestBinding(
            WebExchangeBindException exception,
            ServerWebExchange exchange
    ) {
        List<String> messages = new ArrayList<>();
        for (ObjectError error : exception.getBindingResult().getAllErrors()) {
            messages.add(validationMessage(error));
        }
        String detail = String.join("; ", messages);
        return error(HttpStatus.BAD_REQUEST, "Invalid request",
                detail.isBlank() ? "Request validation failed." : detail, exchange);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException exception,
            ServerWebExchange exchange
    ) {
        String detail = exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "Invalid request",
                detail.isBlank() ? "Request validation failed." : detail, exchange);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            ServerWebExchange exchange
    ) {
        return error(HttpStatus.BAD_REQUEST, "Invalid parameter",
                "Invalid value for parameter '" + exception.getName() + "'.", exchange);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ProblemDetail> handleServerWebInput(
            ServerWebInputException exception,
            ServerWebExchange exchange
    ) {
        String detail = exception.getReason() == null ? "Malformed request." : exception.getReason();
        return error(HttpStatus.BAD_REQUEST, "Invalid request", detail, exchange);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(
            ResponseStatusException exception,
            ServerWebExchange exchange
    ) {
        HttpStatus status = Optional.ofNullable(HttpStatus.resolve(exception.getStatusCode().value()))
                .orElse(HttpStatus.INTERNAL_SERVER_ERROR);
        String detail = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        return error(status, status.getReasonPhrase(), detail, exchange);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
            IllegalArgumentException exception,
            ServerWebExchange exchange
    ) {
        String detail = exception.getMessage() == null ? "Invalid argument." : exception.getMessage();
        return error(HttpStatus.BAD_REQUEST, "Invalid request", detail, exchange);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> handleDataAccess(
            DataAccessException exception,
            ServerWebExchange exchange
    ) {
        log.error("Persistence error while processing {}", exchange.getRequest().getPath().value(), exception);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Catalog temporarily unavailable",
                "The catalog database is temporarily unavailable. Please try again later.", exchange);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception,
            ServerWebExchange exchange
    ) {
        log.error("Unhandled error while processing {}", exchange.getRequest().getPath().value(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
                "An unexpected error occurred. Please try again later.", exchange);
    }

    private static String validationMessage(ObjectError error) {
        String message = error.getDefaultMessage() == null ? "invalid value" : error.getDefaultMessage();
        if (error instanceof FieldError fieldError) {
            return fieldError.getField() + ": " + message;
        }
        return message;
    }

    private static ResponseEntity<ProblemDetail> error(
            HttpStatus status,
            String title,
            String detail,
            ServerWebExchange exchange
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
