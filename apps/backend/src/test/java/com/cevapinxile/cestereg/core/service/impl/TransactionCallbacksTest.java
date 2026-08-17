package com.cevapinxile.cestereg.core.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cevapinxile.cestereg.core.service.support.TransactionCallbacks;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TransactionCallbacksTest {

  @AfterEach
  void clearSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void runsImmediatelyWhenThereIsNoTransactionSynchronization() {
    final AtomicBoolean called = new AtomicBoolean(false);

    TransactionCallbacks.afterCommitOrNow(() -> called.set(true));

    assertTrue(called.get());
  }

  @Test
  void defersUntilAfterCommitWhenTransactionSynchronizationIsActive() {
    final AtomicBoolean called = new AtomicBoolean(false);
    TransactionSynchronizationManager.initSynchronization();

    TransactionCallbacks.afterCommitOrNow(() -> called.set(true));

    assertFalse(called.get());
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    assertTrue(called.get());
  }

  @Test
  void doesNotRunWhenTransactionCompletesWithoutCommit() {
    final AtomicBoolean called = new AtomicBoolean(false);
    TransactionSynchronizationManager.initSynchronization();

    TransactionCallbacks.afterCommitOrNow(() -> called.set(true));
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(
            synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

    assertFalse(called.get());
  }
}
