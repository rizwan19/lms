package com.saasresearcher.learning;

import com.saasresearcher.learning.support.TestInfrastructure;
import org.springframework.boot.SpringApplication;

/** Runs the application against a disposable database: {@code ./gradlew bootTestRun}. Used by Playwright. */
public class TestLearningPortalApplication {

    public static void main(String[] args) {
        SpringApplication.from(LearningPortalApplication::main)
                .with(TestInfrastructure.class)
                .withAdditionalProfiles("test", "e2e")
                .run(args);
    }
}
