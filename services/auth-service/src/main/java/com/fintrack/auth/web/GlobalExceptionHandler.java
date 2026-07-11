package com.fintrack.auth.web;

import com.fintrack.auth.service.EmailAlreadyInUseException;
import com.fintrack.auth.service.InvalidCredentialsException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All errors leave this service as RFC 9457 Problem Details — never ad-hoc
 * bodies (CLAUDE.md convention). Framework-raised errors (404, wrong media
 * type, malformed JSON…) are covered by spring.mvc.problemdetails.enabled;
 * this advice adds the domain-specific and enriched cases.
 *
 * @Order(HIGHEST_PRECEDENCE): Boot's ProblemDetailsExceptionHandler also
 * handles MethodArgumentNotValidException — without an explicit order it wins
 * and clients lose the field-level `errors` extension.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final URI EMAIL_IN_USE_TYPE = URI.create("https://fintrack.example/problems/email-already-in-use");
    private static final URI VALIDATION_TYPE = URI.create("https://fintrack.example/problems/validation-error");
    private static final URI INVALID_CREDENTIALS_TYPE = URI.create("https://fintrack.example/problems/invalid-credentials");

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        // one generic body for wrong-email AND wrong-password — no enumeration
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setType(INVALID_CREDENTIALS_TYPE);
        problem.setTitle("Invalid credentials");
        return problem;
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    ProblemDetail handleEmailAlreadyInUse(EmailAlreadyInUseException ex) {
        // Note: a 409 here reveals that an account exists (user enumeration).
        // Accepted for now — the mitigation (always-202 + email) is a phase-1
        // stretch alongside email verification.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(EMAIL_IN_USE_TYPE);
        problem.setTitle("Email already in use");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setType(VALIDATION_TYPE);
        problem.setTitle("Validation error");

        // field → message map as an RFC 9457 extension member, so clients can
        // attach errors to the right form field
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.merge(fieldError.getField(), fieldError.getDefaultMessage(),
                    (a, b) -> a + "; " + b);
        }
        problem.setProperty("errors", errors);
        return problem;
    }
}