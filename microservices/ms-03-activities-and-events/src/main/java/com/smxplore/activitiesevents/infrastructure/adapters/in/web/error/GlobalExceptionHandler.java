package com.smxplore.activitiesevents.infrastructure.adapters.in.web.error;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityCapacityException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityNameException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityPriceException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityRequestException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityScheduleException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityStatusTransitionException;
import com.smxplore.activitiesevents.domain.exceptions.catalog.CatalogExportNotAllowedException;
import com.smxplore.activitiesevents.domain.exceptions.catalog.InvalidCatalogExportException;
import com.smxplore.activitiesevents.domain.exceptions.event.EventNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.event.EventSiteAccessDeniedException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventCapacityException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventNameException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventPriceException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventRequestException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventScheduleException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventStatusTransitionException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidExpectedAttendanceException;
import com.smxplore.activitiesevents.domain.exceptions.indicator.DemandAccessDeniedException;
import com.smxplore.activitiesevents.domain.exceptions.indicator.InvalidDemandFilterException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.CancellationWindowClosedException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidBookingDateException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidNumberOfPeopleException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationItemException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationItemPriceException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationRequestException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationStatusTransitionException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationTotalException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationTouristException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.ReservationNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.TooManyActiveReservationsException;
import com.smxplore.activitiesevents.domain.exceptions.review.InvalidReviewException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewAlreadyExistsException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewAlreadyReportedException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewAlreadyRespondedException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewFilterUnavailableException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewModerationNotAllowedException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewReportNotAllowedException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewResponseNotAllowedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

import java.net.URI;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({EventNotFoundException.class, ActivityNotFoundException.class,
            ReservationNotFoundException.class, ReviewNotFoundException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(RuntimeException exception, ServerWebExchange exchange) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage(), exchange);
    }

    @ExceptionHandler({InvalidEventStatusTransitionException.class,
            InvalidActivityStatusTransitionException.class,
            InvalidReservationStatusTransitionException.class,
            CancellationWindowClosedException.class,
            TooManyActiveReservationsException.class,
            ReviewAlreadyExistsException.class,
            ReviewAlreadyReportedException.class,
            ReviewAlreadyRespondedException.class})
    public ResponseEntity<ProblemDetail> handleConflict(RuntimeException exception, ServerWebExchange exchange) {
        return build(HttpStatus.CONFLICT, exception.getMessage(), exchange);
    }

    @ExceptionHandler({ReviewResponseNotAllowedException.class, ReviewReportNotAllowedException.class,
            ReviewModerationNotAllowedException.class, DemandAccessDeniedException.class,
            CatalogExportNotAllowedException.class})
    public ResponseEntity<ProblemDetail> handleReviewForbidden(RuntimeException exception,
                                                               ServerWebExchange exchange) {
        return build(HttpStatus.FORBIDDEN, exception.getMessage(), exchange);
    }

    @ExceptionHandler(ReviewFilterUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleReviewFilterUnavailable(
            ReviewFilterUnavailableException exception, ServerWebExchange exchange) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), exchange);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateKey(DuplicateKeyException exception,
                                                            ServerWebExchange exchange) {
        return build(HttpStatus.CONFLICT, "The resource already exists.", exchange);
    }

    @ExceptionHandler({
            InvalidEventRequestException.class,
            InvalidEventNameException.class,
            InvalidEventPriceException.class,
            InvalidEventCapacityException.class,
            InvalidEventScheduleException.class,
            InvalidExpectedAttendanceException.class,
            InvalidActivityRequestException.class,
            InvalidActivityNameException.class,
            InvalidActivityPriceException.class,
            InvalidActivityCapacityException.class,
            InvalidActivityScheduleException.class,
            InvalidReservationRequestException.class,
            InvalidNumberOfPeopleException.class,
            InvalidBookingDateException.class,
            InvalidReservationTotalException.class,
            InvalidReservationTouristException.class,
            InvalidReservationItemException.class,
            InvalidReservationItemPriceException.class,
            InvalidReviewException.class,
            InvalidDemandFilterException.class,
            InvalidCatalogExportException.class
    })
    public ResponseEntity<ProblemDetail> handleBadRequest(RuntimeException exception, ServerWebExchange exchange) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage(), exchange);
    }

    @ExceptionHandler(EventSiteAccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleSiteAccessDenied(EventSiteAccessDeniedException exception,
                                                                ServerWebExchange exchange) {
        return build(HttpStatus.FORBIDDEN, exception.getMessage(), exchange);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ProblemDetail> handleBodyValidation(WebExchangeBindException exception,
                                                              ServerWebExchange exchange) {
        var message = exception.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, message, exchange);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ProblemDetail> handleWebInput(ServerWebInputException exception,
                                                        ServerWebExchange exchange) {
        // Un query param que no convierte (por ejemplo status=BASURA) llega envuelto aqui.
        if (exception.getCause() instanceof TypeMismatchException) {
            return build(HttpStatus.BAD_REQUEST, "Some filter is not valid.", exchange, "INVALID_FILTER");
        }
        return build(HttpStatus.BAD_REQUEST, exception.getReason(), exchange);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException exception,
                                                              ServerWebExchange exchange) {
        return build(HttpStatus.valueOf(exception.getStatusCode().value()), exception.getReason(), exchange);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, ServerWebExchange exchange) {
        log.error("Unexpected error handling {}", exchange.getRequest().getPath(), exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", exchange);
    }

    private ResponseEntity<ProblemDetail> build(HttpStatus status, String message, ServerWebExchange exchange) {
        return build(status, message, exchange, null);
    }

    private ResponseEntity<ProblemDetail> build(HttpStatus status, String message, ServerWebExchange exchange,
                                                String code) {
        var detail = message == null || message.isBlank() ? status.getReasonPhrase() : message;
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
        if (code != null) {
            problem.setProperty("code", code);
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
