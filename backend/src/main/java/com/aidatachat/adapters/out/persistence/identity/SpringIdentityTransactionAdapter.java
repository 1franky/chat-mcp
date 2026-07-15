package com.aidatachat.adapters.out.persistence.identity;

import com.aidatachat.application.port.out.IdentityTransactionPort;
import java.util.function.Supplier;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpringIdentityTransactionAdapter implements IdentityTransactionPort {

    private static final int MAX_ATTEMPTS = 3;

    private final TransactionTemplate serializableTransaction;

    public SpringIdentityTransactionAdapter(PlatformTransactionManager transactionManager) {
        this.serializableTransaction = new TransactionTemplate(transactionManager);
        this.serializableTransaction.setIsolationLevel(
                TransactionDefinition.ISOLATION_SERIALIZABLE);
        this.serializableTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRED);
    }

    @Override
    public <T> T serializable(Supplier<T> operation) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return serializableTransaction.execute(status -> operation.get());
            } catch (TransientDataAccessException exception) {
                if (attempt == MAX_ATTEMPTS) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("Serializable transaction retry loop ended unexpectedly");
    }

    @Override
    public void serializable(Runnable operation) {
        serializable(
                () -> {
                    operation.run();
                    return null;
                });
    }
}
