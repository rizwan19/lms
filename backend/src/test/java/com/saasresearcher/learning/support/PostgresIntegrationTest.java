package com.saasresearcher.learning.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/** Boots the full application on a random port against a disposable PostgreSQL 17 container. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@Import(TestInfrastructure.class)
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    @LocalServerPort
    protected int port;

    @LocalManagementPort
    protected int managementPort;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected JdbcClient jdbc;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    /** A new client with its own cookie jar, i.e. a separate browser session. */
    protected ApiClient client() {
        return new ApiClient("http://localhost:" + port + "/api/v1");
    }

    protected ApiClient managementClient() {
        return new ApiClient("http://localhost:" + managementPort);
    }
}
