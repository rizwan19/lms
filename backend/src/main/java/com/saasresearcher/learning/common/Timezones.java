package com.saasresearcher.learning.common;

import java.time.DateTimeException;
import java.time.ZoneId;

public final class Timezones {

    private Timezones() {
    }

    /** Parses an IANA timezone ID, reporting failures against the given request field. */
    public static ZoneId parse(String field, String value) {
        try {
            return ZoneId.of(value);
        } catch (DateTimeException e) {
            throw ApiException.invalid(field, "Unknown timezone");
        }
    }
}
