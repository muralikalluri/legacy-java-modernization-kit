package com.harborclaims.assess.common;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** One fixed JSON format for every output: 2-space indent, LF line ends, newline at EOF, written atomically. */
public final class Json {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private Json() {
    }

    public static String toString(Object value) throws IOException {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        pp.indentArraysWith(new DefaultIndenter("  ", "\n"));
        pp.indentObjectsWith(new DefaultIndenter("  ", "\n"));
        return MAPPER.writer(pp).writeValueAsString(value) + "\n";
    }

    public static void write(Path target, Object value) throws IOException {
        String text = toString(value);
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, ".assess-", ".tmp");
        try {
            Files.write(tmp, text.getBytes(StandardCharsets.UTF_8));
            try {
                Files.setPosixFilePermissions(tmp, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
            } catch (UnsupportedOperationException ignored) {
                // not a POSIX file system
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public static JsonNode read(Path file) throws IOException {
        return MAPPER.readTree(Files.readAllBytes(file));
    }
}
