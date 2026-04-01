package com.pakgopay.util;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
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

        log.info("transaction start, propagation={}", propagationBehavior);
        template.executeWithoutResult(status -> {
            try {
                log.info("transaction callback enter, propagation={}, stepCount={}", propagationBehavior, steps == null ? 0 : steps.length);
                for (TransactionStep step : steps) {
                    step.execute();
                }
                log.info("transaction callback exit, propagation={}", propagationBehavior);
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("transaction rollback", e);
                throw wrapAsRuntime(e);
            }
        });
        // This log is emitted after TransactionTemplate returns, i.e. after DB commit completed.
        log.info("transaction committed, propagation={}", propagationBehavior);
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

        log.info("transaction start, propagation={}", propagationBehavior);
        T result = template.execute(status -> {
            try {
                log.info("transaction callback enter, propagation={}, callbackType=with_result", propagationBehavior);
                T callbackResult = callback.execute();
                log.info("transaction callback exit, propagation={}, callbackType=with_result", propagationBehavior);
                return callbackResult;
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("transaction rollback", e);
                throw wrapAsRuntime(e);
            }
        });
        // This log is emitted after TransactionTemplate returns, i.e. after DB commit completed.
        log.info("transaction committed, propagation={}", propagationBehavior);
        return result;
    }

    private RuntimeException wrapAsRuntime(Exception e) {
        if (e instanceof PakGoPayException pe) return pe;
        if (e instanceof RuntimeException re) return re;
        return new PakGoPayException(ResultCode.FAIL, e.getMessage());
    }
}
