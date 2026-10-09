package com.college.timetable.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Storage helpers for uploaded files. Uploaded names are never used to build a path. */
public final class UploadStorage {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UploadStorage() {
    }

    /**
     * Derives a collision free internal filename that keeps only a safe extension.
     *
     * @param originalName name supplied by the client, used solely for the extension
     * @param extension    extension including the leading dot, already validated by the caller
     */
    public static String generateInternalName(String extension) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return "import-" + System.currentTimeMillis() + "-"
                + HexFormat.of().formatHex(bytes) + extension;
    }

    /**
     * Keeps at most the last extension, lower cased, and rejects anything with a path separator
     * or control character.
     *
     * @return a safe extension such as {@code .pdf}
     */
    public static String safeExtension(String originalName) {
        if (originalName == null) {
            return "";
        }
        String name = originalName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        String extension = name.substring(dot).toLowerCase(java.util.Locale.ROOT);
        return extension.matches("\\.[a-z0-9]{1,8}") ? extension : "";
    }

    public static Path write(Path directory, String internalName, InputStream in) throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(internalName).normalize();
        if (!target.startsWith(directory.normalize())) {
            throw new IOException("Refusing to write outside the upload directory");
        }
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    public static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort cleanup; never fails a request because a temp file could not be removed.
        }
    }
}