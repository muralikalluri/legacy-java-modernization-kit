package com.harborclaims.assess.jakarta;

import com.fasterxml.jackson.databind.JsonNode;
import com.harborclaims.assess.common.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "jakarta", mixinStandardHelpOptions = true,
        description = "Count javax.* usage and run the OpenRewrite Jakarta recipe as a dry run on a copy.")
public class JakartaCommand implements Callable<Integer> {

    public enum Rewrite {
        /** Run the dry run (needs Maven and Maven Central or a warm ~/.m2). */
        RUN,
        /** Keep the rewrite section of the existing output unchanged (for offline regeneration). */
        REUSE
    }

    @Option(names = "--source", description = "Legacy app directory (default: ${DEFAULT-VALUE})")
    Path source = Path.of("legacy-app");

    @Option(names = "--out", description = "Output file (default: ${DEFAULT-VALUE})")
    Path out = Path.of("assess/results/jakarta.json");

    @Option(names = "--patch-out", description = "Where to keep the dry-run patch as evidence (default: ${DEFAULT-VALUE})")
    Path patchOut = Path.of("assess/results/jakarta-dryrun.patch");

    @Option(names = "--work", description = "Scratch directory for the copy (default: a temp directory OUTSIDE the "
            + "repository: OpenRewrite skips git-ignored paths, so a copy under target/ would parse zero files)")
    Path work;

    @Option(names = "--rewrite", description = "run or reuse (default: ${DEFAULT-VALUE})")
    Rewrite rewrite = Rewrite.RUN;

    @Override
    public Integer call() throws Exception {
        Map<String, Object> block = rewrite == Rewrite.RUN ? runRewrite() : reuse();
        Map<String, Object> result = JakartaBuilder.build(source, block);
        Json.write(out, result);
        System.out.println("jakarta: wrote " + out + " rewrite changedFiles=" + block.get("changedFiles"));
        return 0;
    }

    private Map<String, Object> reuse() throws IOException {
        if (!Files.isRegularFile(out)) {
            throw new IOException("--rewrite reuse needs an existing " + out);
        }
        JsonNode old = Json.read(out).path("rewrite");
        if (old.isMissingNode()) {
            throw new IOException(out + " has no rewrite section to reuse");
        }
        return Json.MAPPER.convertValue(old, new com.fasterxml.jackson.core.type.TypeReference<TreeMap<String, Object>>() {
        });
    }

    private Map<String, Object> runRewrite() throws Exception {
        Path scratch = work != null ? work : Files.createTempDirectory("assess-rewrite-");
        Files.createDirectories(scratch);
        String patch;
        try {
            patch = RewriteRunner.dryRun(source, scratch);
        } finally {
            if (work == null) {
                RewriteRunner.deleteTree(scratch);
            }
        }
        List<PatchParser.Change> changes = PatchParser.parse(patch);
        Map<String, Integer> byKind = new TreeMap<>();
        for (String k : List.of("mainJava", "testJava", "jsp", "pom", "xml", "other")) {
            byKind.put(k, 0);
        }
        List<String> files = new ArrayList<>();
        for (PatchParser.Change c : changes) {
            byKind.merge(PatchParser.kindOf(c.path()), 1, Integer::sum);
            files.add(c.path());
        }
        files.sort(String::compareTo);
        Files.createDirectories(patchOut.toAbsolutePath().getParent());
        Files.write(patchOut, patch.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> block = new TreeMap<>();
        block.put("recipe", RewriteRunner.RECIPE);
        block.put("recipeArtifact", RewriteRunner.RECIPE_ARTIFACT);
        block.put("plugin", RewriteRunner.PLUGIN);
        block.put("mode", "dry run on a copy of legacy-app; no source file was modified");
        block.put("changedFiles", changes.size());
        block.put("changedFilesByKind", byKind);
        block.put("files", files);
        block.put("patchFile", patchOut.getFileName().toString());
        block.put("patchSha256", com.harborclaims.assess.common.Hashing.sha256(patch.getBytes(StandardCharsets.UTF_8)));
        return block;
    }
}
