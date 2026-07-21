package com.fintrack.insight.web;

import com.fintrack.insight.service.FinanceUnavailableException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

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

    private static ProblemDetail withTrace(ProblemDetail problem) {
        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }
}
