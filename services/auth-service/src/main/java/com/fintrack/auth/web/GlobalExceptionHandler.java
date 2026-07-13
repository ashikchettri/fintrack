package com.fintrack.auth.web;

import com.fintrack.auth.service.EmailAlreadyInUseException;
import com.fintrack.auth.service.EmailNotVerifiedException;
import com.fintrack.auth.service.InvalidCredentialsException;
import com.fintrack.auth.service.InvalidRefreshTokenException;
import com.fintrack.auth.service.InvalidResetCodeException;
import com.fintrack.auth.service.InvalidVerificationCodeException;
import com.fintrack.auth.service.IncorrectCurrentPasswordException;
import com.fintrack.auth.service.TooManyLoginAttemptsException;
import org.slf4j.MDC;
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

    private static ProblemDetail withTrace(ProblemDetail problem) {
        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }

    @ExceptionHandler(IncorrectCurrentPasswordException.class)
    ProblemDetail handleIncorrectCurrentPassword(IncorrectCurrentPasswordException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://fintrack.example/problems/incorrect-current-password"));
        problem.setTitle("Incorrect current password");
        return withTrace(problem);
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    ProblemDetail handleEmailNotVerified(EmailNotVerifiedException ex) {
        // distinct type: the UI routes to the verification screen on this
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setType(URI.create("https://fintrack.example/problems/email-not-verified"));
        problem.setTitle("Email not verified");
        return withTrace(problem);
    }

    @ExceptionHandler(InvalidResetCodeException.class)
    ProblemDetail handleInvalidResetCode(InvalidResetCodeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://fintrack.example/problems/invalid-reset-code"));
        problem.setTitle("Invalid reset code");
        return withTrace(problem);
    }

    @ExceptionHandler(InvalidVerificationCodeException.class)
    ProblemDetail handleInvalidVerificationCode(InvalidVerificationCodeException ex) {
        // wrong, expired, over-attempted, unknown email — all identical
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://fintrack.example/problems/invalid-verification-code"));
        problem.setTitle("Invalid verification code");
        return withTrace(problem);
    }

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    ProblemDetail handleTooManyAttempts(TooManyLoginAttemptsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setType(URI.create("https://fintrack.example/problems/too-many-attempts"));
        problem.setTitle("Too many attempts");
        return withTrace(problem);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        // unknown, expired, revoked, reused — all identical from outside
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setType(INVALID_CREDENTIALS_TYPE);
        problem.setTitle("Invalid credentials");
        return withTrace(problem);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        // one generic body for wrong-email AND wrong-password — no enumeration
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setType(INVALID_CREDENTIALS_TYPE);
        problem.setTitle("Invalid credentials");
        return withTrace(problem);
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    ProblemDetail handleEmailAlreadyInUse(EmailAlreadyInUseException ex) {
        // Note: a 409 here reveals that an account exists (user enumeration).
        // Accepted for now — the mitigation (always-202 + email) is a phase-1
        // stretch alongside email verification.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(EMAIL_IN_USE_TYPE);
        problem.setTitle("Email already in use");
        return withTrace(problem);
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
        return withTrace(problem);
    }
}