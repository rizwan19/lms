package com.saasresearcher.learning.institution.api;

import java.time.ZoneId;

public interface InstitutionSettings {

    InstitutionProfile get();

    /** Applies the initial profile during first-administrator setup; caller owns the transaction. */
    void initialize(String name, ZoneId timezone);

    /** Pessimistically locks the singleton settings row to serialize installation-wide changes. */
    void lock();
}
