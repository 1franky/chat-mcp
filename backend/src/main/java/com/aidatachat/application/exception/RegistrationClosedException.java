package com.aidatachat.application.exception;

public final class RegistrationClosedException extends RuntimeException {

    public RegistrationClosedException() {
        super("Public registration is closed");
    }
}
