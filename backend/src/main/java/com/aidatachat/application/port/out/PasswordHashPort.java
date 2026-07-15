package com.aidatachat.application.port.out;

public interface PasswordHashPort {

    String hash(String rawPassword);
}
