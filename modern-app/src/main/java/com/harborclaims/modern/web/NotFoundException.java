package com.harborclaims.modern.web;

/** An unknown, malformed or missing id. Always a 404, never a 400. */
public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String what) {
        super(what);
    }

    /** Parses an id the way legacy does: trimmed Integer.parseInt, so +1 and " 1 " are fine and 0x1, 1 2 and 1.5 are not. */
    public static int id(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (RuntimeException e) {
            throw new NotFoundException("Unknown id");
        }
    }
}
