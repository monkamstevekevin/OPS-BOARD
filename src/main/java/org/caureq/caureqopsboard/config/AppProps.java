package org.caureq.caureqopsboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProps(String apiKey, StatusProps status) {
    public record StatusProps(int upMinutes, int staleMinutes) {}
}