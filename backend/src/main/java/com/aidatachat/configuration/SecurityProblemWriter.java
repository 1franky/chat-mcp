package com.aidatachat.configuration;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class SecurityProblemWriter {

    private SecurityProblemWriter() {}

    static void write(
            HttpServletResponse response, int status, String type, String title, String detail)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/problem+json");
        response.getWriter()
                .write(
                        "{\"type\":\""
                                + type
                                + "\",\"title\":\""
                                + title
                                + "\",\"status\":"
                                + status
                                + ",\"detail\":\""
                                + detail
                                + "\"}");
    }
}
