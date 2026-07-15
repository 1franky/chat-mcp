package com.aidatachat.adapters.out.security;

import com.aidatachat.application.port.out.SessionInvalidationPort;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

public final class SpringSessionInvalidationAdapter implements SessionInvalidationPort {

    private final JdbcIndexedSessionRepository sessions;

    public SpringSessionInvalidationAdapter(JdbcIndexedSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    public void invalidateByPrincipalName(String principalName) {
        sessions.findByPrincipalName(principalName).keySet().forEach(sessions::deleteById);
    }
}
