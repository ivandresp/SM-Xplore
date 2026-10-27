package com.smxplore.users.infrastructure.rest;

import com.smxplore.users.domain.exception.AccountNotActiveException;
import com.smxplore.users.domain.exception.AnomalyAlreadyResolvedException;
import com.smxplore.users.domain.exception.AnomalyNotFoundException;
import com.smxplore.users.domain.exception.DuplicateReviewException;
import com.smxplore.users.domain.exception.EmailAlreadyExistsException;
import com.smxplore.users.domain.exception.ForbiddenOperationException;
import com.smxplore.users.domain.exception.InvalidCredentialsException;
import com.smxplore.users.domain.exception.InvalidCurrentPasswordException;
import com.smxplore.users.domain.exception.InvalidMfaChallengeException;
import com.smxplore.users.domain.exception.InvalidMfaCodeException;
import com.smxplore.users.domain.exception.InvalidPasswordResetTokenException;
import com.smxplore.users.domain.exception.InvalidStatusTransitionException;
import com.smxplore.users.domain.exception.MfaAlreadyEnabledException;
import com.smxplore.users.domain.exception.MfaNotEnabledException;
import com.smxplore.users.domain.exception.ProfileMismatchException;
import com.smxplore.users.domain.exception.ReviewAlreadyReportedException;
import com.smxplore.users.domain.exception.ReviewFilterUnavailableException;
import com.smxplore.users.domain.exception.ReviewNotFoundException;
import com.smxplore.users.domain.exception.TooManyLoginAttemptsException;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.exception.ValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ProblemDetail> handleValidation(ValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
    }

    @ExceptionHandler(ProfileMismatchException.class)
    public ResponseEntity<ProblemDetail> handleProfileMismatch(ProfileMismatchException exception) {
        return problem(HttpStatus.BAD_REQUEST, "PROFILE_MISMATCH", exception.getMessage());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleEmailExists(EmailAlreadyExistsException exception) {
        return problem(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", exception.getMessage());
    }

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyLoginAttempts(TooManyLoginAttemptsException exception) {
        long retryAfterSeconds = Math.max(1, exception.retryAfter().toSeconds());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
        problem.setTitle("Too Many Requests");
        problem.setProperty("code", "TOO_MANY_ATTEMPTS");
        problem.setProperty("retryAfterSeconds", retryAfterSeconds);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
                .body(problem);
    }

    @ExceptionHandler(InvalidCurrentPasswordException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCurrentPassword(InvalidCurrentPasswordException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", exception.getMessage());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ProblemDetail> handleForbiddenOperation(ForbiddenOperationException exception) {
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION", exception.getMessage());
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ProblemDetail> handleInvalidStatusTransition(InvalidStatusTransitionException exception) {
        return problem(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", exception.getMessage());
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ProblemDetail> handleAccountNotActive(AccountNotActiveException exception) {
        return problem(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE", exception.getMessage());
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AuthorizationDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "No tienes permisos para realizar esta operación");
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleUserNotFound(UserNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ReviewNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleReviewNotFound(ReviewNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(AnomalyNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleAnomalyNotFound(AnomalyNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "ANOMALY_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(AnomalyAlreadyResolvedException.class)
    public ResponseEntity<ProblemDetail> handleAnomalyAlreadyResolved(AnomalyAlreadyResolvedException exception) {
        return problem(HttpStatus.CONFLICT, "ANOMALY_ALREADY_RESOLVED", exception.getMessage());
    }

    @ExceptionHandler(DuplicateReviewException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateReview(DuplicateReviewException exception) {
        return problem(HttpStatus.CONFLICT, "DUPLICATE_REVIEW", exception.getMessage());
    }

    @ExceptionHandler(ReviewAlreadyReportedException.class)
    public ResponseEntity<ProblemDetail> handleReviewAlreadyReported(ReviewAlreadyReportedException exception) {
        return problem(HttpStatus.CONFLICT, "REVIEW_ALREADY_REPORTED", exception.getMessage());
    }

    @ExceptionHandler(ReviewFilterUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleReviewFilterUnavailable(ReviewFilterUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "REVIEW_FILTER_UNAVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(InvalidPasswordResetTokenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidResetToken(InvalidPasswordResetTokenException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", exception.getMessage());
    }

    @ExceptionHandler(MfaAlreadyEnabledException.class)
    public ResponseEntity<ProblemDetail> handleMfaAlreadyEnabled(MfaAlreadyEnabledException exception) {
        return problem(HttpStatus.CONFLICT, "MFA_ALREADY_ENABLED", exception.getMessage());
    }

    @ExceptionHandler(MfaNotEnabledException.class)
    public ResponseEntity<ProblemDetail> handleMfaNotEnabled(MfaNotEnabledException exception) {
        return problem(HttpStatus.BAD_REQUEST, "MFA_NOT_ENABLED", exception.getMessage());
    }

    @ExceptionHandler(InvalidMfaCodeException.class)
    public ResponseEntity<ProblemDetail> handleInvalidMfaCode(InvalidMfaCodeException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "INVALID_MFA_CODE", exception.getMessage());
    }

    @ExceptionHandler(InvalidMfaChallengeException.class)
    public ResponseEntity<ProblemDetail> handleInvalidMfaChallenge(InvalidMfaChallengeException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "INVALID_MFA_CHALLENGE", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleBeanValidation(MethodArgumentNotValidException exception) {
        return validationErrors(exception.getBindingResult());
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ProblemDetail> handleWebExchangeBind(WebExchangeBindException exception) {
        return validationErrors(exception.getBindingResult());
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ProblemDetail> handleServerWebInput(ServerWebInputException exception) {
        LOGGER.debug("Solicitud con cuerpo inválido", exception);
        return problem(HttpStatus.BAD_REQUEST, "INVALID_BODY", "El cuerpo de la solicitud no es válido");
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Parámetros inválidos");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_BODY", "El cuerpo de la solicitud no es válido");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "El parámetro enviado no es válido");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException exception) {
        LOGGER.warn("Violación de integridad de datos", exception);
        return problem(HttpStatus.CONFLICT, "DATA_CONFLICT", "Ya existe un registro con los mismos datos");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "CONCURRENT_UPDATE",
                "El registro fue modificado por otra operación; vuelve a intentarlo");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        LOGGER.error("Error no controlado", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Ocurrió un error inesperado");
    }

    private static ResponseEntity<ProblemDetail> validationErrors(BindingResult bindingResult) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            errors.putIfAbsent(
                    fieldError.getField(),
                    fieldError.getDefaultMessage() == null ? "Valor inválido" : fieldError.getDefaultMessage());
        }
        ProblemDetail detail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Los datos enviados no son válidos");
        detail.setTitle("Bad Request");
        detail.setProperty("code", "VALIDATION_ERROR");
        detail.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(detail);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }
}
