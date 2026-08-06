package kz.innlab.starter.shared.transaction

import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Defers a side effect (SMTP send, Telegram/FCM call, SMS provider call) until after the current
 * transaction commits, so external I/O never runs while a DB connection is pinned to an open
 * transaction and never fires for state that ends up rolled back.
 *
 * Runs the action immediately when no transaction is active, so callers work identically
 * inside and outside transactional flows.
 */
@Component
class AfterCommitRunner {

    fun run(action: () -> Unit) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = action()
            })
        } else {
            action()
        }
    }
}
