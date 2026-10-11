package com.bank.expenselimiter.web;

import com.bank.expenselimiter.service.TransactionIngestionService;
import com.bank.expenselimiter.web.dto.IncomingTransactionRequest;
import com.bank.expenselimiter.web.dto.TransactionResponse;
import com.bank.expenselimiter.web.mapper.TransactionMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API A: integration with bank services (incoming expense transactions). */
@RestController
@RequestMapping("/api/v1/integration/transactions")
@Tag(name = "Integration", description = "Intake of expense transactions from bank services")
public class IntegrationTransactionController {

    private final TransactionIngestionService ingestion;
    private final TransactionMapper mapper;

    public IntegrationTransactionController(TransactionIngestionService ingestion, TransactionMapper mapper) {
        this.ingestion = ingestion;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Accept an expense transaction",
            description = "Converts the amount to USD, applies the limit in force and stores the transaction. "
                    + "If the exchange rate is temporarily unavailable the transaction is stored with status "
                    + "PENDING_RATE and completed later; it is never lost.")
    public TransactionResponse accept(@Valid @RequestBody IncomingTransactionRequest request) {
        return mapper.toResponse(ingestion.accept(request.toCommand()));
    }
}
