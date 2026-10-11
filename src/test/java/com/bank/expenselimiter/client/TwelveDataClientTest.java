package com.bank.expenselimiter.client;

import com.bank.expenselimiter.config.AppProperties;
import com.bank.expenselimiter.exception.ExchangeRateUnavailableException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TwelveDataClientTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort()).build();

    private TwelveDataClient client;

    @BeforeEach
    void setUp() {
        AppProperties.TwelveData config = new AppProperties.TwelveData(
                wireMock.baseUrl(), "test-key", Duration.ofSeconds(1), Duration.ofMillis(500), 3, Duration.ofMillis(10));
        client = new TwelveDataClient(RestClient.builder(), config, new SimpleMeterRegistry());
    }

    @Test
    void returnsCloseAndTheActualTradingDay() {
        wireMock.stubFor(get(urlPathEqualTo("/time_series"))
                .withQueryParam("symbol", equalTo("KZT/USD"))
                .withQueryParam("end_date", equalTo("2022-01-30"))
                .withQueryParam("apikey", equalTo("test-key"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"meta":{"symbol":"KZT/USD"},
                         "values":[{"datetime":"2022-01-28","open":"0.0023","high":"0.0023","low":"0.0023","close":"0.00231"}],
                         "status":"ok"}""")));

        RateQuote quote = client.fetchDailyClose("KZT", LocalDate.parse("2022-01-30"));

        assertThat(quote.rate()).isEqualByComparingTo("0.00231");
        assertThat(quote.sourceDate()).isEqualTo(LocalDate.parse("2022-01-28"));
    }

    @Test
    void retriesTransientServerErrorsAndSucceeds() {
        wireMock.stubFor(get(urlPathEqualTo("/time_series")).inScenario("flaky")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        wireMock.stubFor(get(urlPathEqualTo("/time_series")).inScenario("flaky")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"values\":[{\"datetime\":\"2022-01-03\",\"close\":\"0.012\"}],\"status\":\"ok\"}")));

        RateQuote quote = client.fetchDailyClose("RUB", LocalDate.parse("2022-01-03"));

        assertThat(quote.rate()).isEqualByComparingTo("0.012");
        wireMock.verify(2, getRequestedFor(urlPathEqualTo("/time_series")));
    }

    @Test
    void givesUpAfterMaxAttemptsOnPersistentFailure() {
        wireMock.stubFor(get(urlPathEqualTo("/time_series")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.fetchDailyClose("KZT", LocalDate.parse("2022-01-03")))
                .isInstanceOf(ExchangeRateUnavailableException.class);
        wireMock.verify(3, getRequestedFor(urlPathEqualTo("/time_series")));
    }

    @Test
    void slowProviderIsCutOffByReadTimeout() {
        wireMock.stubFor(get(urlPathEqualTo("/time_series"))
                .willReturn(aResponse().withFixedDelay(2000).withStatus(200).withBody("{}")));

        assertThatThrownBy(() -> client.fetchDailyClose("KZT", LocalDate.parse("2022-01-03")))
                .isInstanceOf(ExchangeRateUnavailableException.class);
    }

    @Test
    void providerErrorPayloadIsPermanentAndNotRetried() {
        wireMock.stubFor(get(urlPathEqualTo("/time_series")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":400,\"message\":\"symbol is invalid\",\"status\":\"error\"}")));

        assertThatThrownBy(() -> client.fetchDailyClose("XXX", LocalDate.parse("2022-01-03")))
                .isInstanceOf(ExchangeRateUnavailableException.class)
                .hasMessageContaining("symbol is invalid");
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/time_series")));
    }
}
