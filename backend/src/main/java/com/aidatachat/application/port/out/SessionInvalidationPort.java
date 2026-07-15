package com.aidatachat.application.port.out;

public interface SessionInvalidationPort {

    void invalidateByPrincipalName(String principalName);
}
