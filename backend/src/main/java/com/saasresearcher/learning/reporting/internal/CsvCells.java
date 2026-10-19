package com.saasresearcher.learning.reporting.internal;

/** Spreadsheet safety: cells starting with = + - @ tab or CR are prefixed with an apostrophe so they stay text. */
final class CsvCells {

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
