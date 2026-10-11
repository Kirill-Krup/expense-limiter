package com.bank.expenselimiter.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Base for tests that need the whole application: real HTTP port, PostgreSQL in Testcontainers,
 * external rates API mocked by WireMock, controllable Clock. Requires only a running Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestContainersConfig.class, TestClockConfig.class})
public abstract class AbstractIntegrationTest {

    protected static final WireMockServer WIREMOCK = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.twelvedata.base-url", WIREMOCK::baseUrl);
        registry.add("app.twelvedata.max-attempts", () -> "2");
        registry.add("app.twelvedata.retry-backoff", () -> "PT0.01S");
        registry.add("app.twelvedata.read-timeout", () -> "PT2S");
        registry.add("app.pending.retry-interval", () -> "PT24H"); // the retry job is triggered manually in tests
    }

    @Autowired
    protected MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetState() {
        jdbc.execute("truncate table expense_transaction, spending_limit, exchange_rate restart identity cascade");
        WIREMOCK.resetAll();
        clock.set(Instant.parse("2022-01-01T00:00:00Z"));
    }
}
