package com.fintrack.insight.web;

import com.fintrack.insight.service.AiNotConfiguredException;
import com.fintrack.insight.service.FinanceUnavailableException;
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
 * RFC 9457 Problem Details for insight-service — same contract as the other
 * services, with the request correlation id (ADR 010) stamped into every body.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(FinanceUnavailableException.class)
    ProblemDetail handleFinanceUnavailable(FinanceUnavailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, "Could not load your finance data right now. Please try again.");
        problem.setType(URI.create("https://fintrack.example/problems/finance-unavailable"));
        problem.setTitle("Finance service unavailable");
        return withTrace(problem);
    }

    @ExceptionHandler(AiNotConfiguredException.class)
    ProblemDetail handleAiNotConfigured(AiNotConfiguredException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setType(URI.create("https://fintrack.example/problems/ai-not-configured"));
        problem.setTitle("AI not configured");
        return withTrace(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setType(URI.create("https://fintrack.example/problems/validation-error"));
        problem.setTitle("Validation error");

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.merge(fieldError.getField(), fieldError.getDefaultMessage(), (a, b) -> a + "; " + b);
        }
        problem.setProperty("errors", errors);
        return withTrace(problem);
    }

    private static ProblemDetail withTrace(ProblemDetail problem) {
        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }
}
