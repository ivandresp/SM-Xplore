package com.smxplore.users.shared.infrastructure.rest;

import com.smxplore.users.identity.domain.exception.AccountNotActiveException;
import com.smxplore.users.identity.domain.exception.EmailAlreadyExistsException;
import com.smxplore.users.identity.domain.exception.InvalidCredentialsException;
import com.smxplore.users.identity.domain.exception.InvalidCurrentPasswordException;
import com.smxplore.users.identity.domain.exception.InvalidPasswordResetTokenException;
import com.smxplore.users.identity.domain.exception.ProfileMismatchException;
import com.smxplore.users.identity.domain.exception.UserNotFoundException;
import com.smxplore.users.identity.domain.exception.ValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(InvalidCurrentPasswordException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCurrentPassword(InvalidCurrentPasswordException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", exception.getMessage());
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

    @ExceptionHandler(InvalidPasswordResetTokenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidResetToken(InvalidPasswordResetTokenException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleBeanValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
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

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException exception) {
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

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }
}
