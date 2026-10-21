package com.saasresearcher.learning.media.service;

/** Display filenames: no path segments or control characters, at most 200 code points. Unicode is kept. */
public final class SafeFilenames {

    private SafeFilenames() {
    }

    static String clean(String raw) {
        String name = raw == null ? "" : raw;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"]", "").trim();
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.codePointCount(0, name.length()) > 200) {
            name = name.substring(name.offsetByCodePoints(0, name.codePointCount(0, name.length()) - 200));
        }
        return name.isBlank() ? "upload" : name;
    }

    static String withExtension(String filename, String extension) {
        int dot = filename.lastIndexOf('.');
        return (dot > 0 ? filename.substring(0, dot) : filename) + extension;
    }
}
