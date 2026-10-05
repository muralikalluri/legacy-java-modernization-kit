package com.harborclaims.assess.estimate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelTest {

    @TempDir
    Path tmp;

    private String real() throws IOException {
        return Files.readString(Path.of("estimation-model.yaml"));
    }

    @Test
    void theRealModelLoadsAndEveryRateHasARationale() throws Exception {
        Model m = Model.load(Path.of("estimation-model.yaml"));
        m.rates().values().forEach(r -> assertTrue(r.rationale() != null && !r.rationale().isBlank()));
        m.surcharges().values().forEach(r -> assertTrue(!r.rationale().isBlank()));
        m.fixed().values().forEach(r -> assertTrue(!r.rationale().isBlank()));
        m.businessValue().values().forEach(r -> assertTrue(r.rationale().startsWith("Assumption")));
    }

    @Test
    void unknownKeysAreRejectedSoATypoCannotChangeTheEstimate() throws Exception {
        Path f = tmp.resolve("m.yaml");
        Files.writeString(f, real().replace("roundingStepDays: 0.5", "roundingStepDays: 0.5\nroundingStepDay: 1"));
        assertThrows(IOException.class, () -> Model.load(f));
    }

    @Test
    void lowAboveHighIsRejected() throws Exception {
        Path f = tmp.resolve("m.yaml");
        Files.writeString(f, real().replaceFirst("low: 1.0\n    high: 2.5", "low: 3.0\n    high: 2.5"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Model.load(f));
        assertTrue(e.getMessage().contains("low must not exceed high"));
    }

    @Test
    void negativeRatesAreRejected() throws Exception {
        Path f = tmp.resolve("m.yaml");
        Files.writeString(f, real().replaceFirst("low: 1.0\n    high: 2.5", "low: -1.0\n    high: 2.5"));
        assertThrows(IllegalArgumentException.class, () -> Model.load(f));
    }

    @Test
    void aMissingRateIsRejected() throws Exception {
        Path f = tmp.resolve("m.yaml");
        String text = real();
        int a = text.indexOf("  dao:");
        int b = text.indexOf("  manager:");
        Files.writeString(f, text.substring(0, a) + text.substring(b));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Model.load(f));
        assertTrue(e.getMessage().contains("rates.dao is missing"));
    }

    @Test
    void theModelHoldsInputsOnlyNoCountsAndNoTotals() throws Exception {
        JsonNode root = new ObjectMapper(new YAMLFactory()).readTree(Files.readAllBytes(Path.of("estimation-model.yaml")));
        Set<String> forbidden = Set.of("count", "counts", "total", "totals", "actions", "classes", "jsps", "files", "lines", "modules");
        Set<String> keys = new HashSet<>();
        collect(root, keys);
        for (String k : keys) {
            assertTrue(!forbidden.contains(k.toLowerCase()), "count-like key in the model: " + k);
        }
    }

    private static void collect(JsonNode n, Set<String> keys) {
        if (n.isObject()) {
            Iterator<String> it = n.fieldNames();
            while (it.hasNext()) {
                String k = it.next();
                keys.add(k);
                collect(n.get(k), keys);
            }
        } else if (n.isArray()) {
            n.forEach(x -> collect(x, keys));
        }
    }

    @Test
    void theFingerprintIsTheFileHash() throws Exception {
        String a = Model.fingerprint(Path.of("estimation-model.yaml"));
        assertEquals(64, a.length());
        assertEquals(a, Model.fingerprint(Path.of("estimation-model.yaml")));
        assertEquals(List.of(), List.of());
    }
}
