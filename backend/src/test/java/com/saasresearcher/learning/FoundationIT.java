package com.saasresearcher.learning;

import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

class FoundationIT extends PostgresIntegrationTest {

    @Test
    void databaseAndHealthAreReady() {
        ApiResponse readiness = managementClient().get("/actuator/health/readiness");

        assertThat(readiness.status()).isEqualTo(200);
        assertThat(readiness.json().path("status").asString()).isEqualTo("UP");
        assertThat(jdbc.sql("select 1").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void unknownApiRoutesReturnProblemWithoutStackTrace() {
        ApiResponse response = client().get("/does-not-exist");

        assertThat(response.status()).isIn(401, 404);
        assertThat(response.text()).doesNotContain("Exception").doesNotContain("at com.");
    }
}
