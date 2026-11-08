package com.greenbasket.web;

import com.greenbasket.service.ApiException;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Every error leaves the API as an RFC 7807 problem document: {type,title,status,detail[,errors]}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(title);
        return p;
    }

    @ExceptionHandler(ApiException.class)
    public ProblemDetail api(ApiException e) {
        return problem(e.getStatus(), e.getStatus().getReasonPhrase(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalidBody(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Some fields are invalid.");
        p.setProperty("errors", errors);
        return p;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail invalidParams(ConstraintViolationException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getConstraintViolations().forEach(v -> errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Some parameters are invalid.");
        p.setProperty("errors", errors);
        return p;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ProblemDetail unreadable(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", "The request could not be understood (check field types and values).");
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    public ProblemDetail conflict(Exception e) {
        LOG.warn("Data conflict: {}", org.springframework.core.NestedExceptionUtils.getMostSpecificCause(e).getMessage());
        return problem(HttpStatus.CONFLICT, "Conflict",
                "That change conflicts with existing data or another update in progress. Reload and try again.");
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ProblemDetail busy(PessimisticLockingFailureException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Busy", "The system is busy. Please retry in a moment.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail denied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "Your role is not allowed to do this.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail unauthenticated(AuthenticationException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "Sign in to continue.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception e) {
        LOG.error("Unhandled error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Server error", "Something went wrong on our side.");
    }
}
