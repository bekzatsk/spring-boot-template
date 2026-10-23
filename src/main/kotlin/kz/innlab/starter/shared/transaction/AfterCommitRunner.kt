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
 *
 * Also runs it immediately when called from an action this runner is already running after
 * commit. Synchronization is still active at that point, but Spring invokes afterCommit over a
 * snapshot of the registered synchronizations, so a registration made then never runs. That is
 * how every code email was lost: EmailOtpService deferred sendCode, and the mail service deferred
 * the send a second time.
 */
@Component
class AfterCommitRunner {

    fun run(action: () -> Unit) {
        if (TransactionSynchronizationManager.isSynchronizationActive() && !runningAfterCommit.get()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    runningAfterCommit.set(true)
                    try {
                        action()
                    } finally {
                        runningAfterCommit.remove()
                    }
                }
            })
        } else {
            action()
        }
    }

    private companion object {
        // Static: the default constructor argument in ExternalMailService creates a second
        // instance, and the flag must hold across instances on the same thread.
        val runningAfterCommit: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }
    }
}
