package com.smxplore.securityreports.infrastructure.adapters.in.web.error;

import com.smxplore.securityreports.domain.exceptions.InvalidCommentException;
import com.smxplore.securityreports.domain.exceptions.InvalidCursorException;
import com.smxplore.securityreports.domain.exceptions.InvalidJwtException;
import com.smxplore.securityreports.domain.exceptions.InvalidLocationParamsException;
import com.smxplore.securityreports.domain.exceptions.InvalidPageSizeException;
import com.smxplore.securityreports.domain.exceptions.InvalidReportRequestException;
import com.smxplore.securityreports.domain.exceptions.InvalidStatusTransitionException;
import com.smxplore.securityreports.domain.exceptions.InvalidUserReporterException;
import com.smxplore.securityreports.domain.exceptions.ReportNotFoundException;
import com.smxplore.securityreports.domain.exceptions.ReportWithoutContentException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ReportNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleReportNotFound(ReportNotFoundException exception,
                                                                 ServerWebExchange exchange) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), exchange);
    }

    @ExceptionHandler({
            InvalidReportRequestException.class,
            InvalidLocationParamsException.class,
            InvalidCommentException.class,
            InvalidUserReporterException.class,
            InvalidPageSizeException.class,
            InvalidCursorException.class,
            ReportWithoutContentException.class
    })
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(RuntimeException exception,
                                                                 ServerWebExchange exchange) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), exchange);
    }

    @ExceptionHandler(InvalidJwtException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidJwt(InvalidJwtException exception,
                                                             ServerWebExchange exchange) {
        return error(HttpStatus.UNAUTHORIZED, exception.getMessage(), exchange);
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidStatusTransition(InvalidStatusTransitionException exception,
                                                                          ServerWebExchange exchange) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), exchange);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(OptimisticLockingFailureException exception,
                                                                 ServerWebExchange exchange) {
        return error(HttpStatus.CONFLICT,
                "The report was modified by another request. Please retry with the latest version.", exchange);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(WebExchangeBindException exception,
                                                             ServerWebExchange exchange) {
        return error(HttpStatus.BAD_REQUEST, validationMessage(exception.getBindingResult()), exchange);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception,
                                                                      ServerWebExchange exchange) {
        var message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, message.isBlank() ? "Request validation failed." : message, exchange);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiErrorResponse> handleServerWebInput(ServerWebInputException exception,
                                                                 ServerWebExchange exchange) {
        var message = exception.getReason() == null ? "Malformed request." : exception.getReason();
        return error(HttpStatus.BAD_REQUEST, message, exchange);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception,
                                                                 ServerWebExchange exchange) {
        var status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        var message = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        return error(status, message, exchange);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception,
                                                                  ServerWebExchange exchange) {
        var message = exception.getMessage() == null ? "Invalid request parameter." : exception.getMessage();
        return error(HttpStatus.BAD_REQUEST, message, exchange);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDataAccess(DataAccessException exception,
                                                             ServerWebExchange exchange) {
        log.error("Persistence error while processing {}", exchange.getRequest().getPath().value(), exception);
        return error(HttpStatus.SERVICE_UNAVAILABLE,
                "The persistence service is temporarily unavailable. Please try again later.", exchange);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, ServerWebExchange exchange) {
        log.error("Unhandled error while processing {}", exchange.getRequest().getPath().value(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.", exchange);
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message,
                                                          ServerWebExchange exchange) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status, message, exchange));
    }

    private static String validationMessage(BindingResult bindingResult) {
        var message = Stream.concat(
                        bindingResult.getFieldErrors().stream()
                                .map(error -> error.getField() + ": " + defaultMessage(error)),
                        bindingResult.getGlobalErrors().stream()
                                .map(GlobalExceptionHandler::defaultMessage))
                .distinct()
                .collect(Collectors.joining("; "));
        return message.isBlank() ? "Request validation failed." : message;
    }

    private static String defaultMessage(ObjectError error) {
        return error.getDefaultMessage() == null ? "invalid value" : error.getDefaultMessage();
    }
}
