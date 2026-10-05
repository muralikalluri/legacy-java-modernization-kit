package com.harborclaims.assess.deps;

import com.harborclaims.assess.common.Hashing;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** Lists the jars in a WAR with their SHA-1 and any Maven coordinates found inside them. */
public final class JarScanner {

    public record Coordinates(String groupId, String artifactId, String version) {

        public String ga() {
            return groupId + ":" + artifactId;
        }
    }

    public record Jar(String file, String sha1, List<Coordinates> embedded) {

        /** The embedded coordinate whose artifactId matches the file name, otherwise the first one. */
        public Coordinates primary() {
            for (Coordinates c : embedded) {
                if (file.startsWith(c.artifactId() + "-")) {
                    return c;
                }
            }
            return embedded.isEmpty() ? null : embedded.get(0);
        }
    }

    private JarScanner() {
    }

    public static List<Jar> scanWar(Path war) throws IOException {
        List<Jar> jars = new ArrayList<>();
        try (ZipFile zip = new ZipFile(war.toFile())) {
            List<? extends ZipEntry> entries = Collections.list(zip.entries());
            for (ZipEntry e : entries) {
                String name = e.getName();
                if (name.startsWith("WEB-INF/lib/") && name.endsWith(".jar")) {
                    byte[] bytes;
                    try (InputStream in = zip.getInputStream(e)) {
                        bytes = in.readAllBytes();
                    }
                    jars.add(new Jar(name.substring(name.lastIndexOf('/') + 1), Hashing.sha1(bytes), embedded(bytes)));
                }
            }
        }
        jars.sort((a, b) -> a.file().compareTo(b.file()));
        return jars;
    }

    static List<Coordinates> embedded(byte[] jarBytes) throws IOException {
        List<Coordinates> out = new ArrayList<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarBytes))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                String n = e.getName();
                if (n.startsWith("META-INF/maven/") && n.endsWith("/pom.properties")) {
                    Properties p = new Properties();
                    p.load(zin);
                    String g = p.getProperty("groupId");
                    String a = p.getProperty("artifactId");
                    String v = p.getProperty("version");
                    if (g != null && a != null && v != null) {
                        out.add(new Coordinates(g.trim(), a.trim(), v.trim()));
                    }
                }
            }
        }
        out.sort((x, y) -> (x.ga() + ":" + x.version()).compareTo(y.ga() + ":" + y.version()));
        return out;
    }
}
