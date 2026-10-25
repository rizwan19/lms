package com.saasresearcher.learning.media.service;

import com.saasresearcher.learning.media.api.AssetPurpose;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Accepted upload types (spec §9): declared MIME type, filename extensions and content signature. */
public enum FileType {
    MP4(AssetPurpose.VIDEO, "video/mp4", List.of(".mp4")),
    PDF(AssetPurpose.DOCUMENT, "application/pdf", List.of(".pdf")),
    TEXT(AssetPurpose.DOCUMENT, "text/plain", List.of(".txt")),
    PNG(AssetPurpose.BRANDING_IMAGE, "image/png", List.of(".png")),
    JPEG(AssetPurpose.BRANDING_IMAGE, "image/jpeg", List.of(".jpg", ".jpeg"));

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    final AssetPurpose purpose;
    public final String mimeType;
    final List<String> extensions;

    FileType(AssetPurpose purpose, String mimeType, List<String> extensions) {
        this.purpose = purpose;
        this.mimeType = mimeType;
        this.extensions = extensions;
    }

    public static Optional<FileType> declared(AssetPurpose purpose, String contentType, String filename) {
        String mime = contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        String name = filename.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.purpose == purpose && type.mimeType.equals(mime))
                .filter(type -> type.extensions.stream().anyMatch(name::endsWith))
                .findFirst();
    }

    /** Detects the type from file content, independent of what the client declared. */
    public static Optional<FileType> detect(Path file) throws IOException {
        byte[] head = new byte[16];
        int read;
        try (InputStream in = Files.newInputStream(file)) {
            read = in.readNBytes(head, 0, head.length);
        }
        if (startsWith(head, read, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return Optional.of(PDF);
        }
        if (startsWith(head, read, PNG_SIGNATURE)) {
            return Optional.of(PNG);
        }
        if (read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (read >= 8 && new String(head, 4, 4, StandardCharsets.US_ASCII).equals("ftyp")) {
            return Optional.of(MP4);
        }
        return isUtf8Text(file) ? Optional.of(TEXT) : Optional.empty();
    }

    private static boolean startsWith(byte[] head, int read, byte[] prefix) {
        return read >= prefix.length && Arrays.equals(Arrays.copyOf(head, prefix.length), prefix);
    }

    /** Streams the file through a strict UTF-8 decoder; NUL bytes mark binary content. */
    private static boolean isUtf8Text(Path file) throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer bytes = ByteBuffer.allocate(8192);
        CharBuffer chars = CharBuffer.allocate(8192);
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(bytes.array(), bytes.position(), bytes.remaining())) != -1) {
                bytes.position(bytes.position() + read);
                for (int i = 0; i < bytes.position(); i++) {
                    if (bytes.get(i) == 0) {
                        return false;
                    }
                }
                bytes.flip();
                if (decoder.decode(bytes, chars, false).isError()) {
                    return false;
                }
                bytes.compact();
                chars.clear();
            }
            bytes.flip();
            return !decoder.decode(bytes, chars, true).isError() && !decoder.flush(chars).isError();
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
