package com.saasresearcher.learning.reporting.infra;

/** Spreadsheet safety: cells starting with = + - @ tab or CR are prefixed with an apostrophe so they stay text. */
public final class CsvCells {

    private CsvCells() {
    }

    static String safe(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        return !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0 ? "'" + text : text;
    }
}
