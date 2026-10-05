package com.harborclaims.modern.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings of the auth bridge. The secret comes from the environment, never from a file in the repository. */
@ConfigurationProperties(prefix = "harbor.auth")
public record HarborProperties(String secret, String previousSecret, boolean allowPlaceholderSecret, boolean cookieSecure) {
}
