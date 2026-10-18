package com.saasresearcher.learning.classrooms.api;

import java.util.List;

/**
 * Validation result for every CSV row. {@code digest} identifies the exact normalized rows; it is not an
 * authorization token, and commit revalidates everything.
 */
public record RosterImportPreview(List<Row> rows, int newAccounts, int existingStudents, int invalidRows, String digest) {

    public enum Outcome { NEW_ACCOUNT, ENROLL_EXISTING, INVALID }

    /** One normalized row; {@code line} is the 1-based CSV line number. Never contains credentials. */
    public record Row(int line, String username, String displayName, String email, Outcome outcome, List<String> errors) {
    }

    public boolean valid() {
        return invalidRows == 0 && !rows.isEmpty();
    }
}
