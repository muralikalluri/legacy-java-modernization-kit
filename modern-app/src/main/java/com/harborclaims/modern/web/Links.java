package com.harborclaims.modern.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class Links {

    private Links() {
    }

    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
