package flavor.pie.pieconomy

import org.spongepowered.api.event.cause.Cause
import org.spongepowered.api.service.context.Context
import org.spongepowered.api.service.economy.Currency
import org.spongepowered.api.service.economy.account.Account
import org.spongepowered.api.service.economy.transaction.TransactionResult
import org.spongepowered.api.service.economy.transaction.TransferResult
import java.math.BigDecimal

interface PieconomyAccount : Account {
    override fun resetBalances(cause: Cause, contexts: Set<Context>): Map<Currency, TransactionResult> =
            resetBalances(cause, contexts, true)

    override fun transfer(to: Account, currency: Currency, amount: BigDecimal, cause: Cause, contexts: Set<Context>): TransferResult =
            transfer(to, currency, amount, cause, contexts, true)

    override fun resetBalance(currency: Currency, cause: Cause, contexts: Set<Context>): TransactionResult =
            resetBalance(currency, cause, contexts, true)

    override fun deposit(currency: Currency, amount: BigDecimal, cause: Cause, contexts: Set<Context>): TransactionResult =
            deposit(currency, amount, cause, contexts, true)

    override fun withdraw(currency: Currency, amount: BigDecimal, cause: Cause, contexts: Set<Context>): TransactionResult =
            withdraw(currency, amount, cause, contexts, true)

    override fun setBalance(currency: Currency, amount: BigDecimal, cause: Cause, contexts: Set<Context>): TransactionResult =
            setBalance(currency, amount, cause, contexts, true)

    fun resetBalances(cause: Cause, contexts: Set<Context>, fireEvent: Boolean = true): Map<Currency, TransactionResult>

    fun transfer(to: Account, currency: Currency, amount: BigDecimal, cause: Cause, contexts: Set<Context>, fireEvent: Boolean = true): TransferResult

    fun resetBalance(currency: Currency, cause: Cause, contexts: Set<Context>, fireEvent: Boolean = true): TransactionResult

    fun deposit(currency: Currency, amount: BigDecimal, cause: Cause, contexts: Set<Context>, fireEvent: Boolean = true): TransactionResult

    fun withdraw(currency: Currency, amount: BigDecimal, cause: Cause, contexts: Set<Context>, fireEvent: Boolean = true): TransactionResult

    fun setBalance(currency: Currency, amount: BigDecimal, cause: Cause, contexts: Set<Context>, fireEvent: Boolean = true): TransactionResult
}
