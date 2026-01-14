package com.pakgopay.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
public class TransactionUtil {

    private final PlatformTransactionManager transactionManager;

    /**
     * A business step executed inside transaction.
     */
    @FunctionalInterface
    public interface TransactionStep {
        void execute() throws Exception;
    }

    /**
     * A business callback with return value executed inside transaction.
     */
    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute() throws Exception;
    }

    /**
     * Execute multiple steps in one REQUIRED transaction.
     */
    public void runInTransaction(TransactionStep... steps) {
        runInTransaction(TransactionDefinition.PROPAGATION_REQUIRED, steps);
    }

    /**
     * Execute multiple steps in one transaction with given propagation behavior.
     */
    public void runInTransaction(int propagationBehavior, TransactionStep... steps) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(propagationBehavior);

        template.executeWithoutResult(status -> {
            try {
                for (TransactionStep step : steps) {
                    step.execute();
                }
            } catch (Exception e) {
                status.setRollbackOnly();
                throw wrapAsRuntime(e);
            }
        });
    }

    /**
     * Execute callback in one REQUIRED transaction and return result.
     */
    public <T> T callInTransaction(TransactionCallback<T> callback) {
        return callInTransaction(TransactionDefinition.PROPAGATION_REQUIRED, callback);
    }

    /**
     * Execute callback in one transaction with given propagation behavior and return result.
     */
    public <T> T callInTransaction(int propagationBehavior, TransactionCallback<T> callback) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(propagationBehavior);

        return template.execute(status -> {
            try {
                return callback.execute();
            } catch (Exception e) {
                status.setRollbackOnly();
                throw wrapAsRuntime(e);
            }
        });
    }

    private RuntimeException wrapAsRuntime(Exception e) {
        if (e instanceof RuntimeException re) {
            return re;
        }
        // You can convert to PakGoPayException here if you want
        return new RuntimeException(e);
    }
}

