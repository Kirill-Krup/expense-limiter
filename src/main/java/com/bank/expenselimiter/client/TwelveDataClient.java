package com.bank.expenselimiter.client;

import com.bank.expenselimiter.config.AppProperties;
import com.bank.expenselimiter.exception.ExchangeRateUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.List;

/**
 * twelvedata.com client. Asks for one daily bar with {@code end_date = day}, so the answer is the close of that
 * day, or, on a weekend/holiday, the last close before it (the "previous close" required by the assignment).
 * Timeouts are explicit; transient failures (I/O, 5xx, 429) are retried with a linear backoff, permanent ones are not.
 */
@Component
@ConditionalOnProperty(name = "app.rate-provider", havingValue = "twelvedata", matchIfMissing = true)
public class TwelveDataClient implements ExchangeRateProvider {

    private static final Logger log = LoggerFactory.getLogger(TwelveDataClient.class);
    private static final String METRIC = "exchange_rate.client.requests";

    private final RestClient http;
    private final AppProperties.TwelveData config;
    private final MeterRegistry meters;

    @Autowired
    public TwelveDataClient(RestClient.Builder builder, AppProperties properties, MeterRegistry meters) {
        this(builder, properties.twelvedata(), meters);
    }

    public TwelveDataClient(RestClient.Builder builder, AppProperties.TwelveData config, MeterRegistry meters) {
        HttpClient jdkClient = HttpClient.newBuilder().connectTimeout(config.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkClient);
        factory.setReadTimeout(config.readTimeout());
        this.http = builder.baseUrl(config.baseUrl()).requestFactory(factory).build();
        this.config = config;
        this.meters = meters;
    }

    @Override
    public String name() {
        return "twelvedata";
    }

    @Override
    public RateQuote fetchDailyClose(String currency, LocalDate date) {
        TransientFailure lastFailure = null;
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            Timer.Sample sample = Timer.start(meters);
            try {
                RateQuote quote = callOnce(currency, date);
                stop(sample, "success");
                return quote;
            } catch (TransientFailure e) {
                stop(sample, "transient_error");
                lastFailure = e;
                log.warn("Rate request {}/USD for {} failed (attempt {}/{}): {}",
                        currency, date, attempt, config.maxAttempts(), e.getMessage());
                if (attempt < config.maxAttempts()) {
                    pause(attempt);
                }
            } catch (ExchangeRateUnavailableException e) {
                stop(sample, "permanent_error");
                throw e;
            }
        }
        throw new ExchangeRateUnavailableException(
                "Rate %s/USD for %s unavailable after %d attempts".formatted(currency, date, config.maxAttempts()),
                lastFailure);
    }

    private RateQuote callOnce(String currency, LocalDate date) {
        TimeSeriesResponse body;
        try {
            body = http.get()
                    .uri(uri -> uri.path("/time_series")
                            .queryParam("symbol", currency + "/USD")
                            .queryParam("interval", "1day")
                            .queryParam("end_date", date.toString())
                            .queryParam("outputsize", 1)
                            .queryParam("apikey", config.apiKey())
                            .build())
                    .retrieve()
                    .body(TimeSeriesResponse.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                throw new TransientFailure("rate limited by provider", e);
            }
            throw new ExchangeRateUnavailableException("Provider rejected the request: " + e.getStatusCode(), e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new TransientFailure(e.getMessage(), e);
        } catch (RestClientException e) {
            throw new ExchangeRateUnavailableException("Unreadable provider response", e);
        }
        return toQuote(currency, date, body);
    }

    private RateQuote toQuote(String currency, LocalDate date, TimeSeriesResponse body) {
        if (body == null) {
            throw new ExchangeRateUnavailableException("Empty response for " + currency + "/USD");
        }
        if ("error".equalsIgnoreCase(body.status())) {
            if (Integer.valueOf(429).equals(body.code())) {
                throw new TransientFailure("provider quota exceeded: " + body.message(), null);
            }
            throw new ExchangeRateUnavailableException("Provider error " + body.code() + ": " + body.message());
        }
        if (body.values() == null || body.values().isEmpty()) {
            throw new ExchangeRateUnavailableException("No quotes for %s/USD up to %s".formatted(currency, date));
        }
        Bar bar = body.values().get(0);
        if (bar.close() == null || bar.datetime() == null || bar.datetime().length() < 10) {
            throw new ExchangeRateUnavailableException("Malformed bar for " + currency + "/USD");
        }
        BigDecimal close = new BigDecimal(bar.close());
        if (close.signum() <= 0) {
            throw new ExchangeRateUnavailableException("Non-positive close for " + currency + "/USD");
        }
        return new RateQuote(close, LocalDate.parse(bar.datetime().substring(0, 10)));
    }

    private void stop(Timer.Sample sample, String outcome) {
        sample.stop(Timer.builder(METRIC).tag("provider", name()).tag("outcome", outcome).register(meters));
    }

    private void pause(int attempt) {
        try {
            Thread.sleep(config.retryBackoff().toMillis() * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeRateUnavailableException("Interrupted while waiting to retry", e);
        }
    }

    /** Marker for failures worth retrying. */
    private static final class TransientFailure extends RuntimeException {
        TransientFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TimeSeriesResponse(String status, Integer code, String message, List<Bar> values) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Bar(String datetime, String close) {
    }
}