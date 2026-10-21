package com.saasresearcher.learning.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("learning.identity")
public record IdentityProperties(
        @DefaultValue("12") int passwordHashStrength,
        @DefaultValue("5") int loginAttemptsPerAccount,
        @DefaultValue("50") int loginAttemptsPerIp,
        @DefaultValue("15m") Duration rateLimitWindow,
        @DefaultValue("72h") Duration invitationTtl,
        @DefaultValue("30m") Duration passwordResetTtl,
        @DefaultValue("24h") Duration temporaryPasswordTtl) {
}
