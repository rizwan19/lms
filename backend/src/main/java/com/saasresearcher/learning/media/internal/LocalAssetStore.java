package com.saasresearcher.learning.media.internal;

import com.saasresearcher.learning.media.api.AssetStat;
import com.saasresearcher.learning.media.api.AssetStore;
import com.saasresearcher.learning.media.api.AssetStream;
import com.saasresearcher.learning.media.api.ByteRange;
import com.saasresearcher.learning.media.api.StoredAsset;
import com.saasresearcher.learning.media.api.UploadSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Private filesystem storage: writes to a staging file, then atomically renames into place. */
@Component
@ConditionalOnProperty(name = "learning.storage.mode", havingValue = "local", matchIfMissing = true)
class LocalAssetStore implements AssetStore {

    private static final Pattern KEY = Pattern.compile("^[a-z0-9]+(/[a-z0-9-]+)+$");

    private final Path root;
    private final Path staging;

    LocalAssetStore(MediaProperties properties) throws IOException {
        this.root = properties.storageRoot().toAbsolutePath().normalize();
        this.staging = root.resolve(".staging");
        Files.createDirectories(staging);
    }

    @Override
    public StoredAsset put(UploadSource source) throws IOException {
        Path target = resolve(source.key());
        Path temp = staging.resolve(UUID.randomUUID().toString());
        MessageDigest digest = sha256();
        long size;
        try (InputStream in = source.content();
                OutputStream out = new DigestOutputStream(Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW), digest)) {
            size = in.transferTo(out);
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        Files.createDirectories(target.getParent());
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        return new StoredAsset(source.key(), size, HexFormat.of().formatHex(digest.digest()));
    }

    @Override
    public AssetStat stat(String key) throws IOException {
        return new AssetStat(key, Files.size(resolve(key)));
    }

    @Override
    public AssetStream openRange(String key, ByteRange range) throws IOException {
        Path file = resolve(key);
        long size = Files.size(file);
        FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
        channel.position(range.start());
        InputStream bounded = Channels.newInputStream(channel);
        return new AssetStream(new BoundedInputStream(bounded, range.length()), range, size);
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    @Override
    public void checkAvailable() throws IOException {
        if (!Files.isDirectory(root) || !Files.isWritable(root)) {
            throw new IOException("Media directory is not writable: " + root);
        }
    }

    /** Keys are application-generated; anything else (including traversal attempts) is refused. */
    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
