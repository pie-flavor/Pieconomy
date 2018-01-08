@file:Suppress("ReplaceGetOrSet")

package flavor.pie.pieconomy

import com.google.common.collect.ImmutableMap
import flavor.pie.kludge.*
import org.spongepowered.api.event.cause.Cause
import org.spongepowered.api.item.inventory.ItemStack
import org.spongepowered.api.item.inventory.Slot
import org.spongepowered.api.service.context.Context
import org.spongepowered.api.service.economy.Currency
import org.spongepowered.api.service.economy.EconomyService
import org.spongepowered.api.service.economy.account.Account
import org.spongepowered.api.service.economy.account.UniqueAccount
import org.spongepowered.api.service.economy.transaction.ResultType
import org.spongepowered.api.service.economy.transaction.TransactionResult
import org.spongepowered.api.service.economy.transaction.TransactionTypes
import org.spongepowered.api.service.economy.transaction.TransferResult
import org.spongepowered.api.text.Text
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

class PieconomyPlayerAccount(val id: UUID) : PieconomyAccount, UniqueAccount {
    val config get() = Pieconomy.instance.config

    override fun getIdentifier(): String = id.toString()

    override fun hasBalance(currency: Currency, contexts: Set<Context>): Boolean {
        val p = id.player() ?: return false
        return p.storageInventory.get(*config.items.filter { (_, value) ->
            value.currency == currency
        }.keys.map(ItemVariant::toItem).toTypedArray()).peek().isPresent
    }

    override fun resetBalances(cause: Cause, contexts: Set<Context>, fireEvent: Boolean): Map<Currency, TransactionResult> {
        val svc: EconomyService by UncheckedService
        return svc.currencies.map { it to resetBalance(it, cause, contexts, fireEvent) }.toMap()
    }

    override fun getBalance(currency: Currency, contexts: Set<Context>): BigDecimal {
        val p = id.player() ?: return BigDecimal.ZERO
        return p.storageInventory.get(*config.items.filter { (_, value) -> value.currency == currency }
                .keys.map(ItemVariant::toItem).toTypedArray()).slots
                .map { it.peek().get().let { config.items[it.type, it.data]!!.amount * BigDecimal(it.quantity) } }
                .fold(BigDecimal.ZERO, BigDecimal::add)
    }

    override fun getBalances(contexts: Set<Context>): Map<Currency, BigDecimal> {
        val svc: EconomyService by UncheckedService
        val builder = ImmutableMap.builder<Currency, BigDecimal>()
        svc.currencies.forEach {
            if (hasBalance(it)) {
                builder.put(it, getBalance(it))
            }
        }
        return builder.build()
    }

    override fun getDisplayName(): Text {
        val player = id.player() ?: return !id.toString()
        return !player.name
    }

    override fun getUniqueId(): UUID = id

    override fun transfer(to: Account, currency: Currency, amount: BigDecimal, cause: Cause, contexts: Set<Context>, fireEvent: Boolean): TransferResult {
        val res = (to as? PieconomyAccount)?.deposit(currency, amount, cause, contexts, false) ?: to.deposit(currency, amount, cause, contexts)
        if (res.result != ResultType.SUCCESS) {
            return PieconomyTransferResult(this, amount, contexts, currency, res.result, to, false).also { if (fireEvent) post(it, cause) }
        }
        val res2 = withdraw(currency, amount, cause, contexts, false)
        if (res2.result != ResultType.SUCCESS) {
            to.withdraw(currency, amount, cause, contexts)
            return PieconomyTransferResult(this, amount, contexts, currency, res2.result, to, true).also { if (fireEvent) post(it, cause) }
        }
        return PieconomyTransferResult(this, amount, contexts, currency, ResultType.SUCCESS, to, false).also { if (fireEvent) post(it, cause) }
    }

    override fun getDefaultBalance(currency: Currency): BigDecimal = BigDecimal.ZERO

    override fun resetBalance(currency: Currency, cause: Cause, contexts: Set<Context>, fireEvent: Boolean): TransactionResult {
        val p = id.player() ?: return PieconomyTransactionResult(this, BigDecimal.ZERO, contexts, currency, ResultType.FAILED, TransactionTypes.WITHDRAW).also { if (fireEvent) post(it, cause) }
        var num = BigDecimal.ZERO
        config.items.filter { (_, value) -> value.currency == currency }.forEach { (type, _) ->
            p.storageInventory[type.toItem()].slots<Slot>().forEach { it.poll().ifPresent { num += BigDecimal(it.quantity) } }
        }
        return PieconomyTransactionResult(this, num, contexts, currency, ResultType.SUCCESS, TransactionTypes.WITHDRAW).also { if (fireEvent) post(it, cause) }
    }

    override fun deposit(currency: Currency, amount: BigDecimal, cause: Cause, contexts: Set<Context>, fireEvent: Boolean): TransactionResult {
        if (amount < BigDecimal.ZERO) {
            throw IllegalArgumentException("amount cannot be negative")
        }
        val p = id.player() ?: return PieconomyTransactionResult(this, amount, contexts, currency, ResultType.FAILED, TransactionTypes.DEPOSIT).also { if (fireEvent) post(it, cause) }
        val items = config.items.filter { (_, value) -> value.currency == currency }.mapValues { it.value.amount }
                .let { it.toSortedMap(Comparator.comparing<ItemVariant, BigDecimal> { v -> it[v] }.reversed()) }
        val min = items[items.lastKey()]!!
        var left = amount
        val inserted: MutableList<ItemStack> = ArrayList()
        loop@for ((item, value) in items) {
            while (left >= value) {
                val used = minOf(item.type.maxStackQuantity, left.divide(value, RoundingMode.DOWN).toInt())
                val toInsert = ItemStack.of(item.type, used).withData(item.data)
                val res = p.storageInventory.offer(toInsert)
                toInsert.quantity = used - toInsert.quantity
                left -= value * BigDecimal(toInsert.quantity)
                inserted += toInsert
                if (!res.rejectedItems.isEmpty()) {
                    continue@loop
                }
            }
            if (left < min) {
                break
            }
        }
        if (left >= min) {
            for (insert in inserted) {
                p.storageInventory[insert].poll(insert.quantity)
            }
            return PieconomyTransactionResult(this, amount, contexts, currency, ResultType.ACCOUNT_NO_SPACE, TransactionTypes.DEPOSIT).also { if (fireEvent) post(it, cause) }
        }
//        for (item in list) {
//            inserted += item
//            val quantity = item.quantity
//            val res = p.storageInventory.offer(item)
//            item.quantity = quantity - item.quantity
//            if (!res.rejectedItems.isEmpty()) {
//                for (insert in inserted) {
//                    p.storageInventory.queryAny<Inventory>(insert).poll(insert.quantity)
//                }
//                return PieconomyTransactionResult(this, amount, contexts, currency, ResultType.ACCOUNT_NO_SPACE, TransactionTypes.DEPOSIT).also { if (fireEvent) post(it, cause) }
//            }
//        }
        return PieconomyTransactionResult(this, amount, contexts, currency, ResultType.SUCCESS, TransactionTypes.DEPOSIT).also { if (fireEvent) post(it, cause) }
    }

    override fun withdraw(currency: Currency, amount: BigDecimal, cause: Cause, contexts: Set<Context>, fireEvent: Boolean): TransactionResult {
        if (amount < BigDecimal.ZERO) {
            throw IllegalArgumentException("amount cannot be negative")
        }
        val p = id.player() ?: return PieconomyTransactionResult(this, amount, contexts, currency, ResultType.FAILED, TransactionTypes.WITHDRAW).also { if (fireEvent) post(it, cause) }
        val items = config.items.filter { (_, value) -> value.currency == currency }.mapValues { it.value.amount }
                .let { it.toSortedMap(Comparator.comparing<ItemVariant, BigDecimal> { v -> it[v] }.reversed()) }
        val min = items[items.lastKey()]!!
        if (amount - getBalance(currency, contexts) > min) {
            return PieconomyTransactionResult(this, amount, contexts, currency, ResultType.ACCOUNT_NO_FUNDS, TransactionTypes.WITHDRAW).also { if (fireEvent) post(it, cause) }
        }
        var left = amount
        val removed = ArrayList<ItemStack>()
        for ((item, value) in items) {
            if (value > left) {
                continue
            }
            val query = p.storageInventory[item.toItem()]
            val available = minOf((left / value).toInt(), query.totalItems())
            var used = 0
            while (used < available) {
                val polled = query.poll(minOf(item.type.maxStackQuantity, available)).get()
                if (polled.quantity == 0) {
                    break
                }
                used += polled.quantity
                removed += polled
            }
            left -= BigDecimal(used) * value
            if (left < min) {
                break
            }
        }
        if (left >= min) {
            val reversed = items.keys.reversed()
            var item: ItemStack? = null
            for (type in reversed) {
                val removedItem = !p.storageInventory[type.toItem()].poll(1) ?: continue
                val itemPrice = items[type]!!
                val change = itemPrice - left
                val res = deposit(currency, change, cause, contexts)
                if (res.result == ResultType.SUCCESS) {
                    item = removedItem
                    break
                } else {
                    p.storageInventory.offer(removedItem)
                }
            }
            if (item == null) {
                removed.forEach { p.storageInventory.offer(it) }
                return PieconomyTransactionResult(this, amount, contexts, currency, ResultType.ACCOUNT_NO_SPACE, TransactionTypes.WITHDRAW).also { if (fireEvent) post(it, cause) }
            }
        }
        return PieconomyTransactionResult(this, amount, contexts, currency, ResultType.SUCCESS, TransactionTypes.WITHDRAW).also { if (fireEvent) post(it, cause) }
    }

    override fun getActiveContexts(): Set<Context> = setOf()

    override fun setBalance(currency: Currency, amount: BigDecimal, cause: Cause, contexts: Set<Context>, fireEvent: Boolean): TransactionResult {
        id.player() ?: return PieconomyTransactionResult(this, BigDecimal.ZERO, contexts, currency, ResultType.FAILED, TransactionTypes.DEPOSIT).also { if (fireEvent) post(it, cause) }
        val items = config.items.filter { (_, value) -> value.currency == currency }.mapValues { it.value.amount }
                .let { it.toSortedMap(Comparator.comparing<ItemVariant, BigDecimal> { v -> it[v] }.reversed()) }
        val min = items[items.lastKey()]!!
        if (amount < min) {
            return resetBalance(currency, cause, contexts, fireEvent)
        }
        val bal = getBalance(currency, contexts)
        return when {
            bal > amount -> withdraw(currency, bal - amount, cause, contexts, fireEvent)
            bal == amount -> PieconomyTransactionResult(this, BigDecimal.ZERO, contexts, currency, ResultType.SUCCESS, TransactionTypes.DEPOSIT).also { if (fireEvent) post(it, cause) }
            bal < amount -> deposit(currency, amount - bal, cause, contexts, fireEvent)
            else -> error("Logic and reason have deserted us. Whee!")
        }
    }

    private fun post(res: TransactionResult, cause: Cause) = EventManager.post(PieconomyTransactionEvent(cause, res))
}
