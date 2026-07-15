package com.aidatachat.application.port.out;

import java.util.function.Supplier;

public interface IdentityTransactionPort {

    <T> T serializable(Supplier<T> operation);

    void serializable(Runnable operation);
}
