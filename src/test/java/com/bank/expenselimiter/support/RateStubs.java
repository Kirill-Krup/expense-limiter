package com.bank.expenselimiter.support;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/** WireMock stubs for the Twelve Data time_series endpoint. */
public final class RateStubs {

    private RateStubs() {
    }

    /** Any requested day answers with the given close (USD per 1 unit of {@code currency}). */
    public static void close(WireMockServer wireMock, String currency, String close) {
        wireMock.stubFor(get(urlPathEqualTo("/time_series"))
                .withQueryParam("symbol", equalTo(currency + "/USD"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"meta":{"symbol":"%s/USD"},
                                 "values":[{"datetime":"2022-01-03","open":"%s","high":"%s","low":"%s","close":"%s"}],
                                 "status":"ok"}""".formatted(currency, close, close, close, close))));
    }

    public static void serverError(WireMockServer wireMock) {
        wireMock.stubFor(get(urlPathEqualTo("/time_series")).willReturn(aResponse().withStatus(500)));
    }
}
