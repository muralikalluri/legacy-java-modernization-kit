package com.harborclaims.assess.inventory;

import java.util.List;

/** Which javax.* packages move to jakarta.* and which are owned by the JDK and stay as they are. */
public final class Javax {

    public static final String JAKARTA = "JAKARTA_MIGRATING";
    public static final String JDK = "JDK_OWNED";
    public static final String OTHER = "OTHER";

    private static final List<String> JDK_EXCEPTIONS = List.of("javax.annotation.processing", "javax.transaction.xa");

    private static final List<String> JAKARTA_PREFIXES = List.of("javax.servlet", "javax.el", "javax.persistence",
            "javax.ws.rs", "javax.annotation", "javax.mail", "javax.transaction", "javax.validation", "javax.inject",
            "javax.ejb", "javax.jms", "javax.faces", "javax.json", "javax.enterprise", "javax.websocket",
            "javax.security.enterprise", "javax.batch", "javax.activation", "javax.xml.bind", "javax.xml.ws",
            "javax.xml.soap", "javax.jws");

    private static final List<String> JDK_PREFIXES = List.of("javax.sql", "javax.naming", "javax.xml", "javax.crypto",
            "javax.net", "javax.management", "javax.script", "javax.tools", "javax.imageio", "javax.swing", "javax.print",
            "javax.sound", "javax.rmi", "javax.security.auth", "javax.security.cert", "javax.security.sasl",
            "javax.lang.model", "javax.activity", "javax.accessibility", "javax.smartcardio");

    private Javax() {
    }

    private static boolean matches(String pkg, List<String> prefixes) {
        for (String p : prefixes) {
            if (pkg.equals(p) || pkg.startsWith(p + ".")) {
                return true;
            }
        }
        return false;
    }

    public static String bucket(String pkg) {
        if (matches(pkg, JDK_EXCEPTIONS)) {
            return JDK;
        }
        if (matches(pkg, JAKARTA_PREFIXES)) {
            return JAKARTA;
        }
        if (matches(pkg, JDK_PREFIXES)) {
            return JDK;
        }
        return OTHER;
    }

    /** The leading lower-case segments of a dotted name: javax.servlet.http.HttpServletRequest gives javax.servlet.http. */
    public static String packageOf(String dotted) {
        StringBuilder sb = new StringBuilder();
        for (String seg : dotted.split("\\.")) {
            if (seg.isEmpty() || !Character.isLowerCase(seg.charAt(0))) {
                break;
            }
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(seg);
        }
        return sb.toString();
    }
}
