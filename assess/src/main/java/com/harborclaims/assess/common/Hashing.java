package com.harborclaims.assess.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class Hashing {

    private Hashing() {
    }

    public static String hex(String algorithm, byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256(byte[] data) {
        return hex("SHA-256", data);
    }

    public static String sha1(byte[] data) {
        return hex("SHA-1", data);
    }

    public static String sha256(Path file) throws IOException {
        return sha256(Files.readAllBytes(file));
    }

    /** Fingerprint of a set of "name:hash" lines. The caller passes them in any order. */
    public static String fingerprint(List<String> lines) {
        List<String> sorted = lines.stream().sorted().toList();
        return sha256(String.join("\n", sorted).getBytes(StandardCharsets.UTF_8));
    }
}
