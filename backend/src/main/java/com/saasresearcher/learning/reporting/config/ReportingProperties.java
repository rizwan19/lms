package com.saasresearcher.learning.reporting.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Students without acknowledged activity for this many days (1-90, default 7) are flagged inactive. */
@Validated
@ConfigurationProperties("learning.reporting")
public record ReportingProperties(@DefaultValue("7") @Min(1) @Max(90) int inactivityDays) {
}
