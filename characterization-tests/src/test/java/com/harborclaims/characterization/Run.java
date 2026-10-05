package com.harborclaims.characterization;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Per-run state. Every row a test creates carries the run token in its last name and e-mail, so sort-sensitive tests can
 * search for the token and get a data set nobody else touches. Seeded rows are never modified.
 */
public final class Run {

    /** Two capitals-and-lower-case letters then eight lower-case letters: no seeded fragment, ASCII, sorts after seeded names. */
    public static final String TOKEN = "Zq" + randomLetters(8);
    public static final String EMAIL_DOMAIN = "char.example.test";

    private static final Map<String, Session> SESSIONS = new HashMap<>();
    private static int counter;

    static {
        System.out.println("characterization run token: " + TOKEN + " target=" + Target.describe());
    }

    private Run() {
    }

    private static String randomLetters(int n) {
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append((char) ('a' + r.nextInt(26)));
        }
        return sb.toString();
    }

    /** One signed-in session per demo user, shared by tests that only read. */
    public static synchronized Session as(String username) {
        return SESSIONS.computeIfAbsent(username, Session::login);
    }

    /** A last name unique within the run, such as Zqabcdefgh007. */
    public static synchronized String lastName(String label) {
        counter++;
        return TOKEN + label + String.format("%03d", counter);
    }

    public static String emailFor(String lastName) {
        return lastName.toLowerCase() + "@" + EMAIL_DOMAIN;
    }
}
