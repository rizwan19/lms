package com.saasresearcher.learning.reporting.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Students without acknowledged activity for this many days (1-90, default 7) are flagged inactive. */
@Validated
@ConfigurationProperties("learning.reporting")
record ReportingProperties(@DefaultValue("7") @Min(1) @Max(90) int inactivityDays) {
}
