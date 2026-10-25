package com.saasresearcher.learning.media.service;

import com.saasresearcher.learning.media.config.MediaProperties;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks staged uploads: detected content type must match the declared type, videos must be
 * H.264/AAC MP4 (inspected with ffprobe, never transcoded), and branding images are decoded and
 * re-encoded as PNG. Validation is not a malware-free guarantee.
 */
@Component
public class UploadValidator {

    private static final int MAX_IMAGE_DIMENSION = 4096;

    private final MediaProperties properties;
    private final JsonMapper json;

    UploadValidator(MediaProperties properties, JsonMapper json) {
        this.properties = properties;
        this.json = json;
    }

    record Validated(Path file, String contentType, Integer durationSeconds) {
    }

    Validated validate(FileType declared, Path staged) throws IOException, RejectedUpload {
        FileType detected = FileType.detect(staged).orElseThrow(() -> RejectedUpload.invalid("Unrecognised file content"));
        if (detected != declared) {
            throw RejectedUpload.invalid("File content does not match its declared type");
        }
        return switch (declared) {
            case MP4 -> new Validated(staged, declared.mimeType, probeVideo(staged));
            case PNG, JPEG -> new Validated(reencodeImage(staged), FileType.PNG.mimeType, null);
            case PDF, TEXT -> new Validated(staged, declared.mimeType, null);
        };
    }

    private int probeVideo(Path file) throws IOException, RejectedUpload {
        Process process = new ProcessBuilder(properties.ffprobePath(), "-v", "error", "-print_format", "json",
                "-show_entries", "stream=codec_type,codec_name:format=duration", file.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        JsonNode result;
        try {
            result = json.readTree(process.getInputStream());
            if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw RejectedUpload.invalid("Video could not be read");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while probing video", e);
        } finally {
            process.destroyForcibly();
        }
        List<JsonNode> streams = result.path("streams").valueStream().toList();
        boolean h264Video = streams.stream().anyMatch(s -> s.path("codec_type").asString().equals("video") && s.path("codec_name").asString().equals("h264"));
        boolean otherVideo = streams.stream().anyMatch(s -> s.path("codec_type").asString().equals("video") && !s.path("codec_name").asString().equals("h264"));
        boolean nonAacAudio = streams.stream().anyMatch(s -> s.path("codec_type").asString().equals("audio") && !s.path("codec_name").asString().equals("aac"));
        if (!h264Video || otherVideo || nonAacAudio) {
            throw RejectedUpload.invalid("Video must be an MP4 with H.264 video and AAC audio");
        }
        double duration = result.path("format").path("duration").asDouble(0);
        return (int) Math.round(duration);
    }

    /** Decodes within dimension bounds and writes a fresh PNG, dropping metadata and trailing data. */
    private Path reencodeImage(Path file) throws IOException, RejectedUpload {
        BufferedImage image;
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw RejectedUpload.invalid("Image could not be read");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (reader.getWidth(0) > MAX_IMAGE_DIMENSION || reader.getHeight(0) > MAX_IMAGE_DIMENSION) {
                    throw RejectedUpload.invalid("Image dimensions exceed " + MAX_IMAGE_DIMENSION + " pixels");
                }
                image = reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw RejectedUpload.invalid("Image could not be read");
        }
        Path output = file.resolveSibling(file.getFileName() + ".png");
        ImageIO.write(image, "png", output.toFile());
        return output;
    }

    static void deleteQuietly(Path... files) {
        for (Path file : files) {
            try {
                if (file != null) {
                    Files.deleteIfExists(file);
                }
            } catch (IOException ignored) {
                // best effort; abandoned staging files are swept later
            }
        }
    }
}
