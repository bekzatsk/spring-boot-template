package kz.innlab.starter.shared.transaction

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionSynchronizationUtils

class AfterCommitRunnerTest {

    private val runner = AfterCommitRunner()

    @AfterEach
    fun clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `runs immediately without a transaction`() {
        val ran = mutableListOf<String>()
        runner.run { ran += "action" }
        assertEquals(listOf("action"), ran)
    }

    @Test
    fun `defers the action until commit`() {
        TransactionSynchronizationManager.initSynchronization()
        val ran = mutableListOf<String>()

        runner.run { ran += "action" }
        assertEquals(emptyList<String>(), ran)

        TransactionSynchronizationUtils.triggerAfterCommit()
        assertEquals(listOf("action"), ran)
    }

    @Test
    fun `an action deferred from inside afterCommit still runs`() {
        // EmailOtpService defers sendCode, and the mail service defers the send again. Spring runs
        // afterCommit over a snapshot of the registered synchronizations, so a second registration
        // made during afterCommit never ran and the code email was silently dropped.
        TransactionSynchronizationManager.initSynchronization()
        val ran = mutableListOf<String>()

        runner.run { runner.run { ran += "nested" } }
        TransactionSynchronizationUtils.triggerAfterCommit()

        assertEquals(listOf("nested"), ran)
    }

    @Test
    fun `the afterCommit phase ends with the action, even when it throws`() {
        TransactionSynchronizationManager.initSynchronization()
        runner.run { error("provider down") }
        runCatching { TransactionSynchronizationUtils.triggerAfterCommit() }
        TransactionSynchronizationManager.clearSynchronization()

        // A new transaction on the same (pooled) thread must defer again, not run at once.
        TransactionSynchronizationManager.initSynchronization()
        val ran = mutableListOf<String>()
        runner.run { ran += "action" }
        assertEquals(emptyList<String>(), ran)
    }
}
