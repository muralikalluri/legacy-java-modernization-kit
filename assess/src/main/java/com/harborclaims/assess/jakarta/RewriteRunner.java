package com.harborclaims.assess.jakarta;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/**
 * Runs the OpenRewrite dry run on a COPY of the legacy app, so nothing is written into legacy-app/
 * (the plugin writes target/rewrite/rewrite.patch next to the pom it runs on). Versions are pinned.
 */
public final class RewriteRunner {

    public static final String PLUGIN = "org.openrewrite.maven:rewrite-maven-plugin:6.46.1";
    public static final String RECIPE_ARTIFACT = "org.openrewrite.recipe:rewrite-migrate-java:3.42.1";
    public static final String RECIPE = "org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta";

    private RewriteRunner() {
    }

    public static List<String> command() {
        return List.of("mvn", "-B", PLUGIN + ":dryRun",
                "-Drewrite.recipeArtifactCoordinates=" + RECIPE_ARTIFACT,
                "-Drewrite.activeRecipes=" + RECIPE,
                "-Drewrite.exportDatatables=false");
    }

    /** Returns the patch text ("" when the recipe changes nothing). Any failure is an exception, never "0 changes". */
    public static String dryRun(Path legacyApp, Path workDir) throws IOException, InterruptedException {
        Path copy = workDir.resolve("legacy-app");
        deleteTree(copy);
        copyTree(legacyApp, copy);
        Path log = workDir.resolve("rewrite.log");
        ProcessBuilder pb = new ProcessBuilder(command()).directory(copy.toFile()).redirectErrorStream(true)
                .redirectOutput(log.toFile());
        int exit = pb.start().waitFor();
        if (exit != 0) {
            String tail = Files.readAllLines(log).stream().skip(Math.max(0, Files.readAllLines(log).size() - 15))
                    .reduce("", (a, b) -> a + b + "\n");
            throw new IOException("OpenRewrite dry run failed (exit " + exit + "). Last output:\n" + tail);
        }
        requireResult(Files.readString(log), Files.isRegularFile(copy.resolve("target/rewrite/rewrite.patch")));
        Path patch = copy.resolve("target/rewrite/rewrite.patch");
        return Files.isRegularFile(patch) ? Files.readString(patch, StandardCharsets.UTF_8) : "";
    }

    /** A run that neither wrote a patch nor said "no changes" proves nothing, so it is an error, not zero changes. */
    static void requireResult(String log, boolean patchExists) throws IOException {
        if (!patchExists && !log.contains("Applying recipes would make no changes")) {
            throw new IOException("OpenRewrite finished without a result line; refusing to treat that as 0 changes.");
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.getFileName() != null && dir.getFileName().toString().equals("target") && dir.getParent().equals(from)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
