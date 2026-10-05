package com.harborclaims.assess.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Sorted, POSIX-relative file listings so output never depends on the file system's walk order. */
public final class SourceTree {

    private SourceTree() {
    }

    public static List<Path> files(Path dir, Predicate<Path> filter) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).filter(filter).sorted((a, b) -> rel(dir, a).compareTo(rel(dir, b))).toList();
        }
    }

    public static String rel(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
