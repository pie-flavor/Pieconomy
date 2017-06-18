package flavor.pie.pieconomy

import org.spongepowered.api.service.context.Context
import org.spongepowered.api.service.economy.Currency
import org.spongepowered.api.service.economy.account.Account
import org.spongepowered.api.service.economy.transaction.ResultType
import org.spongepowered.api.service.economy.transaction.TransactionResult
import org.spongepowered.api.service.economy.transaction.TransactionType
import org.spongepowered.api.service.economy.transaction.TransactionTypes
import org.spongepowered.api.service.economy.transaction.TransferResult
import java.math.BigDecimal

open class PieconomyTransactionResult(private val account: Account, private val amount: BigDecimal,
                                      private val contexts: Set<Context>, private val currency: Currency,
                                      private val result: ResultType, private val type: TransactionType) : TransactionResult {
    override fun getResult(): ResultType = result
    override fun getCurrency(): Currency = currency
    override fun getContexts(): Set<Context> = contexts
    override fun getType(): TransactionType = type
    override fun getAccount(): Account = account
    override fun getAmount(): BigDecimal = amount
}

class PieconomyTransferResult(account: Account, amount: BigDecimal, contexts: Set<Context>,
                              currency: Currency, result: ResultType, private val accountTo: Account,
                              val payerFailed: Boolean)
    : PieconomyTransactionResult(account, amount, contexts, currency, result, TransactionTypes.TRANSFER), TransferResult {
    override fun getAccountTo(): Account = accountTo

}