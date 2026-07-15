package com.aidatachat.application.exception;

public final class LastAdministratorException extends RuntimeException {

    public LastAdministratorException() {
        super("The last active administrator cannot be changed");
    }
}
