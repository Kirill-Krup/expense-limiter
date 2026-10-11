package com.bank.expenselimiter.web;

import com.bank.expenselimiter.service.LimitService;
import com.bank.expenselimiter.service.TransactionQueryService;
import com.bank.expenselimiter.web.dto.ExceededTransactionResponse;
import com.bank.expenselimiter.web.dto.LimitResponse;
import com.bank.expenselimiter.web.dto.SetLimitRequest;
import com.bank.expenselimiter.web.mapper.LimitMapper;
import com.bank.expenselimiter.web.mapper.TransactionMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API B: client-facing requests (limits and exceeded transactions). There is deliberately no PUT/PATCH/DELETE. */
@RestController
@RequestMapping("/api/v1/client")
@Tag(name = "Client", description = "Limits and transactions that exceeded them")
public class ClientController {

    private static final String ACCOUNT_REGEX = "\\d{10}";

    private final LimitService limits;
    private final TransactionQueryService queries;
    private final LimitMapper limitMapper;
    private final TransactionMapper transactionMapper;

    public ClientController(LimitService limits, TransactionQueryService queries,
                            LimitMapper limitMapper, TransactionMapper transactionMapper) {
        this.limits = limits;
        this.queries = queries;
        this.limitMapper = limitMapper;
        this.transactionMapper = transactionMapper;
    }

    @PostMapping("/limits")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Set a new monthly limit (USD)",
            description = "The effective date is the current server time and cannot be provided or changed. "
                    + "Existing limits are never updated.")
    public LimitResponse setLimit(@Valid @RequestBody SetLimitRequest request) {
        return limitMapper.toResponse(limits.setLimit(request.account(), request.expenseCategory(), request.limitSum()));
    }

    @GetMapping("/limits")
    @Operation(summary = "All limits of an account, newest first")
    public List<LimitResponse> getLimits(
            @RequestParam @NotNull @Pattern(regexp = ACCOUNT_REGEX, message = "must be exactly 10 digits") String account) {
        return limits.findAll(account).stream().map(limitMapper::toResponse).toList();
    }

    @GetMapping("/transactions/exceeded")
    @Operation(summary = "Transactions that exceeded the limit, with the limit that was exceeded")
    public List<ExceededTransactionResponse> getExceeded(
            @RequestParam @NotNull @Pattern(regexp = ACCOUNT_REGEX, message = "must be exactly 10 digits") String account) {
        return queries.findExceeded(account).stream().map(transactionMapper::toExceeded).toList();
    }
}
