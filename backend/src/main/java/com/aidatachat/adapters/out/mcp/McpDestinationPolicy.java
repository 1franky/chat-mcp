package com.aidatachat.adapters.out.mcp;

import com.aidatachat.application.exception.McpCommunicationException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

public final class McpDestinationPolicy {

    public void validate(URI uri) {
        String host = uri.getHost();
        if (host == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            blocked();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) {
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
            throw new McpCommunicationException("MCP_DNS_FAILED", null, true, null);
        }
    }

    private void blocked() {
        throw new McpCommunicationException("MCP_DESTINATION_BLOCKED", null, false, null);
    }
}
