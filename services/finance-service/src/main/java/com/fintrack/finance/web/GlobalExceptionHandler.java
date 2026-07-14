package com.fintrack.finance.web;

import com.fintrack.finance.service.AccountNotFoundException;
import com.fintrack.finance.service.csv.CsvImportException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RFC 9457 Problem Details for finance-service — same contract as auth-service.
 * @Order(HIGHEST_PRECEDENCE) so this wins over Boot's default validation handler
 * and clients keep the field-level `errors` extension.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail handleAccountNotFound(AccountNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create("https://fintrack.example/problems/account-not-found"));
        problem.setTitle("Account not found");
        return problem;
    }

    @ExceptionHandler(CsvImportException.class)
    ProblemDetail handleCsvImport(CsvImportException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://fintrack.example/problems/csv-import-failed"));
        problem.setTitle("CSV import failed");
        return problem;
    }

    /** A missing `file` part or `currency` param on the import endpoint is a 400. */
    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    ProblemDetail handleMissingPart(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://fintrack.example/problems/csv-import-failed"));
        problem.setTitle("CSV import failed");
        return problem;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONTENT_TOO_LARGE, "The uploaded file is too large.");
        problem.setType(URI.create("https://fintrack.example/problems/upload-too-large"));
        problem.setTitle("Upload too large");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setType(URI.create("https://fintrack.example/problems/validation-error"));
        problem.setTitle("Validation error");

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.merge(fieldError.getField(), fieldError.getDefaultMessage(),
                    (a, b) -> a + "; " + b);
        }
        problem.setProperty("errors", errors);
        return problem;
    }
}
