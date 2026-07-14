package com.fintrack.finance.web;

import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.service.ImportSummary;
import com.fintrack.finance.service.TransactionImportService;
import com.fintrack.finance.service.csv.CsvImportException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * The hero endpoint: upload a bank CSV, get back a summary of what landed. The
 * statement's own "Account" column becomes real accounts, rows are deduped, and
 * the response feeds straight into the dashboard.
 */
@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final TransactionImportService importService;

    public ImportController(TransactionImportService importService) {
        this.importService = importService;
    }

    @PostMapping(path = "/transactions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImportSummary importTransactions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file,
            // statements don't carry a currency; the uploader states it (default AUD)
            @RequestParam(value = "currency", defaultValue = "AUD") String currency) throws IOException {

        if (file.isEmpty()) {
            throw new CsvImportException("The uploaded file is empty.");
        }
        if (!currency.matches("[A-Za-z]{3}")) {
            throw new CsvImportException("currency must be a 3-letter ISO 4217 code");
        }
        try (InputStream in = file.getInputStream()) {
            return importService.importCsv(
                    AuthenticatedMember.from(jwt), file.getOriginalFilename(), currency, in);
        }
    }
}
