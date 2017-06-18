package flavor.pie.pieconomy

import org.spongepowered.api.event.cause.Cause
import org.spongepowered.api.event.economy.EconomyTransactionEvent
import org.spongepowered.api.service.economy.transaction.TransactionResult

class PieconomyTransactionEvent(private val cause: Cause,
                                private val transactionResult: TransactionResult) : EconomyTransactionEvent {
    override fun getCause() = cause

    override fun getTransactionResult() = transactionResult

}