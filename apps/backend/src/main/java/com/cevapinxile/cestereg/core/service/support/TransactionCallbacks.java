package com.cevapinxile.cestereg.core.service.support;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Runs side effects only after a successful transaction commit when synchronization is active. */
public final class TransactionCallbacks {

  private TransactionCallbacks() {}

  public static void afterCommitOrNow(final Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            action.run();
          }
        });
  }
}
