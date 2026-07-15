package com.aidatachat.adapters.out.provider;

import com.aidatachat.application.exception.ProviderCommunicationException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class ProviderDestinationPolicy {

    private final Set<String> allowedHosts;
    private final Set<String> allowedHttpHosts;

    public ProviderDestinationPolicy(String allowedHosts, String allowedHttpHosts) {
        this.allowedHosts = split(allowedHosts);
        this.allowedHttpHosts = split(allowedHttpHosts);
    }

    public void validateCustomDestination(URI uri) {
        String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
        if (host == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || !allowedHosts.contains(host)) {
            blocked();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https")
                && !(scheme.equals("http") && allowedHttpHosts.contains(host))) {
            blocked();
        }
        int port = uri.getPort();
        if (port == 0 || port > 65_535) {
            blocked();
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress()
                        || address.isLinkLocalAddress()
                        || address.isMulticastAddress()) {
                    blocked();
                }
            }
        } catch (UnknownHostException exception) {
            throw new ProviderCommunicationException("PROVIDER_DNS_FAILED", null, true, null);
        }
    }

    private Set<String> split(String values) {
        if (values == null || values.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(values.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private void blocked() {
        throw new ProviderCommunicationException("PROVIDER_DESTINATION_BLOCKED", null, false, null);
    }
}
