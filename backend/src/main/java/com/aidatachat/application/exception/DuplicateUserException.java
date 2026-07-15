package com.aidatachat.application.exception;

public final class DuplicateUserException extends RuntimeException {

    public DuplicateUserException() {
        super("A user with that identity already exists");
    }
}
