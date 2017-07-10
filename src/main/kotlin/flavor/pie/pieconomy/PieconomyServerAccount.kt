package flavor.pie.pieconomy

import com.google.common.collect.ImmutableMap
import flavor.pie.kludge.*
import org.spongepowered.api.event.cause.Cause
import org.spongepowered.api.service.context.Context
import org.spongepowered.api.service.economy.Currency
import org.spongepowered.api.service.economy.EconomyService
import org.spongepowered.api.service.economy.account.Account
import org.spongepowered.api.service.economy.account.VirtualAccount
import org.spongepowered.api.service.economy.transaction.ResultType
import org.spongepowered.api.service.economy.transaction.TransactionResult
import org.spongepowered.api.service.economy.transaction.TransactionTypes
import org.spongepowered.api.service.economy.transaction.TransferResult
import org.spongepowered.api.text.Text
import java.math.BigDecimal

class PieconomyServerAccount(val name: String) : PieconomyAccount, VirtualAccount {
    val money: MutableMap<Currency, BigDecimal> = HashMap()
    lateinit var currencies: List<Currency>
    lateinit var negativeValues: List<Currency>
    val svc: EconomyService by UncheckedService
    override fun hasBalance(currency: Currency, contexts: Set<Context>): Boolean = currency in money

    override fun resetBalances(cause: Cause, contexts: Set<Context>, fireEvent: Boolean): Map<Currency, TransactionResult> =
        money.keys.map { it to resetBalance(it, cause, contexts, fireEvent) }.toMap()

    override fun getBalance(currency: Currency, contexts: Set<Context>): BigDecimal = money[currency] ?: BigDecimal.ZERO

    override fun getBalances(contexts: Set<Context>): Map<Currency, BigDecimal> = ImmutableMap.copyOf(money)

    override fun getDisplayName(): Text = !name

    override fun transfer(to: Account, currency: Currency, amount: BigDecimal, cause: Cause, contexts: Set<Context>, fireEvent: Boolean): TransferResult {
        val res1 = withdraw(currency, amount, cause, contexts, false)
        if (res1.result != ResultType.SUCCESS) {
            return PieconomyTransferResult(this, amount, contexts, currency, res1.result, to, true).also { if (fireEvent) post(it, cause) }
        }
        val res2 = (to as? PieconomyAccount)?.deposit(currency, amount, cause, contexts, false) ?: to.deposit(currency, amount, cause, contexts)
        if (res2.result != ResultType.SUCCESS) {
            deposit(currency, amount, cause, contexts, false)
            return PieconomyTransferResult(this, amount, contexts, currency, res2.result, to, false).also { if (fireEvent) post(it, cause) }
        }
        return PieconomyTransferResult(this, amount, contexts, currency, ResultType.SUCCESS, to, false)
    }

    override fun getDefaultBalance(currency: Currency): BigDecimal = BigDecimal.ZERO

    override fun resetBalance(currency: Currency, cause: Cause, contexts: Set<Context>, fireEvent: Boolean): TransactionResult {
        money[currency] = BigDecimal.ZERO
        return PieconomyTransactionResult(this, BigDecimal.ZERO, contexts, currency, ResultType.SUCCESS, TransactionTypes.WITHDRAW).also { if (fireEvent) post(it, cause) }
    }

    override fun deposit(currency: Currency, amount: BigDecimal, cause: Cause, contexts: Set<Context>, fireEvent: Boolean): TransactionResult {
        if (currency !in currencies) {
            return PieconomyTransactionResult(this, amount, contexts, currency, ResultType.ACCOUNT_NO_SPACE, TransactionTypes.DEPOSIT).also { if (fireEvent) post(it, cause) }
        }
        money[currency] = (money[currency] ?: BigDecimal.ZERO) + amount
        return PieconomyTransactionResult(this, amount, contexts, currency, ResultType.SUCCESS, TransactionTypes.DEPOSIT).also { if (fireEvent) post(it, cause) }
    }

    override fun withdraw(currency: Currency, amount: BigDecimal, cause: Cause, contexts: Set<Context>, fireEvent: Boolean): TransactionResult {
        if (currency !in money) {
            return PieconomyTransactionResult(this, amount, contexts, currency, ResultType.ACCOUNT_NO_FUNDS, TransactionTypes.WITHDRAW).also { if (fireEvent) post(it, cause) }
        }
        val bal = money[currency]!!
        if (bal < amount && currency !in negativeValues) {
            return PieconomyTransactionResult(this, amount, contexts, currency, ResultType.ACCOUNT_NO_FUNDS, TransactionTypes.WITHDRAW).also { if (fireEvent) post(it, cause) }
        }
        money[currency] = bal - amount
        return PieconomyTransactionResult(this, amount, contexts, currency, ResultType.SUCCESS, TransactionTypes.WITHDRAW).also { if (fireEvent) post(it, cause) }
    }

    override fun getActiveContexts(): Set<Context> = setOf()

    override fun setBalance(currency: Currency, amount: BigDecimal, cause: Cause, contexts: Set<Context>, fireEvent: Boolean): TransactionResult {
        if (currency !in currencies) {
            return PieconomyTransactionResult(this, amount, contexts, currency, ResultType.ACCOUNT_NO_SPACE, TransactionTypes.WITHDRAW).also { if (fireEvent) post(it, cause) }
        }
        money[currency] = amount
        return PieconomyTransactionResult(this, amount, contexts, currency, ResultType.SUCCESS, TransactionTypes.WITHDRAW).also { if (fireEvent) post(it, cause) }
    }

    override fun getIdentifier(): String = name

    fun post(result: TransactionResult, cause: Cause) {
        EventManager.post(PieconomyTransactionEvent(cause, result))
    }
}
