package com.saasresearcher.learning.identity.service;

import com.saasresearcher.learning.common.ApiException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalization and validation for usernames, emails and passwords (spec §6). */
public final class AccountRules {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,64}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    static final int PASSWORD_MIN = 12;
    static final int PASSWORD_MAX = 128;

    private AccountRules() {
    }

    static String username(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!USERNAME.matcher(trimmed).matches()) {
            throw ApiException.invalid("username", "Use 3-64 letters, digits, dots, underscores or hyphens");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    /** Returns the normalized email, or null when absent. */
    static String email(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !EMAIL.matcher(normalized).matches()) {
            throw ApiException.invalid("email", "Enter a valid email address");
        }
        return normalized;
    }

    static String displayName(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.codePointCount(0, trimmed.length()) > 150) {
            throw ApiException.invalid("displayName", "Display name must be 1-150 characters");
        }
        return trimmed;
    }

    static void password(String field, String value) {
        if (value == null || value.length() < PASSWORD_MIN || value.length() > PASSWORD_MAX) {
            throw ApiException.invalid(field, "Password must be " + PASSWORD_MIN + "-" + PASSWORD_MAX + " characters");
        }
    }
}
