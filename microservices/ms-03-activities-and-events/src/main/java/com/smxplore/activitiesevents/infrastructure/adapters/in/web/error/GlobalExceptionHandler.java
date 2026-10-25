package com.smxplore.activitiesevents.infrastructure.adapters.in.web.error;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityCapacityException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityNameException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityPriceException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityRequestException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityScheduleException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityStatusTransitionException;
import com.smxplore.activitiesevents.domain.exceptions.event.EventNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventCapacityException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventNameException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventPriceException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventRequestException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventScheduleException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventStatusTransitionException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidExpectedAttendanceException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.DifferentProvidersInReservationException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidBookingDateException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidNumberOfPeopleException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationItemException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationItemPriceException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationRequestException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationStatusTransitionException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationTotalException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationTouristException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.MixedReservationItemTypesException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.ReservationItemNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.ReservationItemsNotAllowedException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.ReservationNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.TooManyReservationItemsException;
import com.smxplore.activitiesevents.domain.exceptions.review.InvalidReviewException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewAlreadyExistsException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewAlreadyRespondedException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewResponseNotAllowedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({EventNotFoundException.class, ActivityNotFoundException.class,
            ReservationNotFoundException.class, ReservationItemNotFoundException.class,
            ReviewNotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(RuntimeException exception, ServerWebExchange exchange) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage(), exchange);
    }

    @ExceptionHandler({InvalidEventStatusTransitionException.class,
            InvalidActivityStatusTransitionException.class,
            InvalidReservationStatusTransitionException.class,
            ReservationItemsNotAllowedException.class,
            TooManyReservationItemsException.class,
            ReviewAlreadyExistsException.class,
            ReviewAlreadyRespondedException.class})
    public ResponseEntity<ApiErrorResponse> handleConflict(RuntimeException exception, ServerWebExchange exchange) {
        return build(HttpStatus.CONFLICT, exception.getMessage(), exchange);
    }

    @ExceptionHandler(ReviewResponseNotAllowedException.class)
    public ResponseEntity<ApiErrorResponse> handleReviewForbidden(RuntimeException exception,
                                                                  ServerWebExchange exchange) {
        return build(HttpStatus.FORBIDDEN, exception.getMessage(), exchange);
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
            MixedReservationItemTypesException.class,
            DifferentProvidersInReservationException.class,
            InvalidReviewException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(RuntimeException exception, ServerWebExchange exchange) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage(), exchange);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiErrorResponse> handleBodyValidation(WebExchangeBindException exception,
                                                                 ServerWebExchange exchange) {
        var message = exception.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, message, exchange);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(ServerWebInputException exception,
                                                                 ServerWebExchange exchange) {
        return build(HttpStatus.BAD_REQUEST, exception.getReason(), exchange);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception,
                                                                 ServerWebExchange exchange) {
        return build(HttpStatus.valueOf(exception.getStatusCode().value()), exception.getReason(), exchange);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, ServerWebExchange exchange) {
        log.error("Unexpected error handling {}", exchange.getRequest().getPath(), exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", exchange);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, ServerWebExchange exchange) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status, message, exchange));
    }
}
