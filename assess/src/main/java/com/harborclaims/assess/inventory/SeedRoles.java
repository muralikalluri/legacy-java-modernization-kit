package com.harborclaims.assess.inventory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Business roles come from the multi-row INSERT INTO roles in the seed. DB login roles are not business roles. */
public final class SeedRoles {

    private static final Pattern INSERT = Pattern.compile("(?is)INSERT\\s+INTO\\s+roles\\b.*?;");
    private static final Pattern ROW = Pattern.compile("\\(\\s*\\d+\\s*,\\s*'([A-Za-z_]+)'");

    private SeedRoles() {
    }

    public static List<String> parse(Path seedSql) throws IOException {
        return parse(Files.readString(seedSql, StandardCharsets.UTF_8));
    }

    public static List<String> parse(String sql) {
        List<String> roles = new ArrayList<>();
        Matcher insert = INSERT.matcher(sql);
        while (insert.find()) {
            Matcher row = ROW.matcher(insert.group());
            while (row.find()) {
                roles.add(row.group(1));
            }
        }
        return roles;
    }
}
