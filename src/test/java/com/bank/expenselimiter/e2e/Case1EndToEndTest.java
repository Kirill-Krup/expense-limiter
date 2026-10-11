package com.bank.expenselimiter.e2e;

import com.bank.expenselimiter.support.AbstractIntegrationTest;
import com.bank.expenselimiter.support.RateStubs;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Replays "case 1" of the assignment through the real HTTP API:
 * real port + PostgreSQL in Testcontainers + WireMock instead of the external rates API.
 * Sums are sent in KZT at a mocked rate of 0.01 USD, so 50000.00 KZT == 500.00 USD.
 */
class Case1EndToEndTest extends AbstractIntegrationTest {

    private static final String ACCOUNT = "0000000123";

    @LocalServerPort
    int port;

    @Test
    void caseOne_returnsExactlyTransactionsOf3rdAndThe13thWithExceededLimit() {
        RestClient api = RestClient.create("http://localhost:" + port);
        RateStubs.close(WIREMOCK, "KZT", "0.01");

        // limit of 1000 USD set on 01.01.2022
        clock.set(OffsetDateTime.parse("2022-01-01T00:00:00+06:00").toInstant());
        postLimit(api, "1000.00");

        postTransaction(api, "50000.00", "2022-01-02T10:00:00+06:00"); // 500  -> remainder 500
        postTransaction(api, "60000.00", "2022-01-03T10:00:00+06:00"); // 600  -> remainder -100, EXCEEDED

        // new limit of 2000 USD set on 10.01.2022
        clock.set(OffsetDateTime.parse("2022-01-10T00:00:00+06:00").toInstant());
        postLimit(api, "2000.00");

        postTransaction(api, "10000.00", "2022-01-11T10:00:00+06:00"); // 100 -> 800
        postTransaction(api, "70000.00", "2022-01-12T10:00:00+06:00"); // 700 -> 100
        postTransaction(api, "10000.00", "2022-01-13T10:00:00+06:00"); // 100 -> 0
        postTransaction(api, "10000.00", "2022-01-13T11:00:00+06:00"); // 100 -> -100, EXCEEDED

        JsonNode response = api.get()
                .uri("/api/v1/client/transactions/exceeded?account={a}", ACCOUNT)
                .retrieve().body(JsonNode.class);
        List<JsonNode> exceeded = StreamSupport.stream(response.spliterator(), false).toList();

        assertThat(exceeded).hasSize(2);

        JsonNode third = exceeded.get(0);
        assertThat(OffsetDateTime.parse(third.get("datetime").asText()).toInstant())
                .isEqualTo(instant("2022-01-03T10:00:00+06:00"));
        assertThat(third.get("sum").decimalValue()).isEqualByComparingTo("60000.00");
        assertThat(third.get("currency_shortname").asText()).isEqualTo("KZT");
        assertThat(third.get("limit_sum").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(OffsetDateTime.parse(third.get("limit_datetime").asText()).toInstant())
                .isEqualTo(instant("2022-01-01T00:00:00+06:00"));
        assertThat(third.get("limit_currency_shortname").asText()).isEqualTo("USD");

        JsonNode thirteenth = exceeded.get(1);
        assertThat(OffsetDateTime.parse(thirteenth.get("datetime").asText()).toInstant())
                .isEqualTo(instant("2022-01-13T11:00:00+06:00"));
        assertThat(thirteenth.get("limit_sum").decimalValue()).isEqualByComparingTo("2000.00");
        assertThat(OffsetDateTime.parse(thirteenth.get("limit_datetime").asText()).toInstant())
                .isEqualTo(instant("2022-01-10T00:00:00+06:00"));
        assertThat(thirteenth.get("limit_currency_shortname").asText()).isEqualTo("USD");

        // rates are paid for once per day: two transactions on 13.01 -> a single provider request
        WIREMOCK.verify(1, getRequestedFor(urlPathEqualTo("/time_series"))
                .withQueryParam("end_date", equalTo("2022-01-13")));
    }

    @Test
    void invalidTransactionIsRejectedWithProblemDetail() {
        RestClient api = RestClient.create("http://localhost:" + port);
        Map<String, Object> invalid = Map.of(
                "account_from", "123", "account_to", "9999999999", "currency_shortname", "KZT",
                "sum", new BigDecimal("10.00"), "expense_category", "product",
                "datetime", "2022-01-30T00:00:00+06:00");

        assertThatThrownBy(() -> api.post().uri("/api/v1/integration/transactions").body(invalid).retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(HttpClientErrorException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(400);
                    assertThat(e.getResponseBodyAsString()).contains("\"errors\"").contains("account_from");
                });
    }

    @Test
    void clientCannotProvideTheLimitDate() {
        RestClient api = RestClient.create("http://localhost:" + port);
        Map<String, Object> withDate = Map.of(
                "account", ACCOUNT, "expense_category", "product", "limit_sum", new BigDecimal("500.00"),
                "limit_datetime", "2021-01-01T00:00:00+06:00");

        assertThatThrownBy(() -> api.post().uri("/api/v1/client/limits").body(withDate).retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(HttpClientErrorException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
    }

    private void postLimit(RestClient api, String sum) {
        api.post().uri("/api/v1/client/limits")
                .body(Map.of("account", ACCOUNT, "expense_category", "product", "limit_sum", new BigDecimal(sum)))
                .retrieve().toBodilessEntity();
    }

    private void postTransaction(RestClient api, String sumKzt, String datetime) {
        api.post().uri("/api/v1/integration/transactions")
                .body(Map.of(
                        "account_from", ACCOUNT,
                        "account_to", "9999999999",
                        "currency_shortname", "KZT",
                        "sum", new BigDecimal(sumKzt),
                        "expense_category", "product",
                        "datetime", datetime))
                .retrieve().toBodilessEntity();
    }

    private static Instant instant(String isoWithOffset) {
        return OffsetDateTime.parse(isoWithOffset).toInstant();
    }
}
