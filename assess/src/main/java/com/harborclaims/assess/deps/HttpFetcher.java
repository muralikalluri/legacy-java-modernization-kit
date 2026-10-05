package com.harborclaims.assess.deps;

import java.io.IOException;

/** The only way the tool touches the network. Tests inject a fake. */
public interface HttpFetcher {

    record Response(int status, String body) {
    }

    Response post(String url, String jsonBody) throws IOException;
}
