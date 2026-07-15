package com.aidatachat.adapters.out.provider;

import java.net.URI;

final class ProviderUris {

    private ProviderUris() {}

    static URI append(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String suffix = path.startsWith("/") ? path : "/" + path;
        return URI.create(base + suffix);
    }
}
