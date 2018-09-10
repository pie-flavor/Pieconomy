package flavor.pie.pieconomy

import flavor.pie.kludge.*
import org.spongepowered.api.command.CommandException
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.CommandSource
import org.spongepowered.api.command.args.CommandContext
import org.spongepowered.api.data.type.HandTypes
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.service.economy.Currency
import org.spongepowered.api.service.economy.EconomyService
import org.spongepowered.api.service.economy.account.Account
import org.spongepowered.api.service.economy.transaction.ResultType
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import flavor.pie.util.arguments.MoreArguments as MoreArgs
import org.spongepowered.api.command.args.GenericArguments as Args

object Commands {
    val svc: EconomyService by UncheckedService

    fun register() {
        val pay = CommandSpec {
            executor(Commands::pay)
            description(!"Pay money to someone else.")
            arguments(
                    AccountElement(!"to"),
                    MoreArgs.bigDecimal(!"amount"),
                    Args.optionalWeak(Args.catalogedElement(!"currency", Currency::class.java))
            )
        }
        val bal = CommandSpec {
            executor(Commands::bal)
            description(!"Check your (or someone else's) balance.")
            arguments(
                    Args.optionalWeak(AccountElement((!"who"))),
                    Args.optionalWeak(Args.catalogedElement(!"currency", Currency::class.java))
            )
        }
        val deposit = CommandSpec {
            executor(Commands::deposit)
            description(!"Give money to an account.")
            arguments(
                    AccountElement(!"to"),
                    MoreArgs.bigDecimal(!"amount"),
                    Args.optionalWeak(Args.catalogedElement(!"currency", Currency::class.java))
            )
        }
        val withdraw = CommandSpec {
            executor(Commands::withdraw)
            description(!"Remove money from an account.")
            arguments(
                    AccountElement(!"from"),
                    MoreArgs.bigDecimal(!"amount"),
                    Args.optionalWeak(Args.catalogedElement(!"currency", Currency::class.java))
            )
        }
        val transfer = CommandSpec {
            executor(Commands::transfer)
            description(!"Transfer money between two accounts.")
            arguments(
                    AccountElement(!"from"),
                    AccountElement(!"to"),
                    MoreArgs.bigDecimal(!"amount"),
                    Args.optionalWeak(Args.catalogedElement(!"currency", Currency::class.java))
            )
        }
        val setbal = CommandSpec {
            executor(Commands::setbal)
            description(!"Set an account's money to a certain value.")
            arguments(
                    AccountElement(!"who"),
                    MoreArgs.bigDecimal(!"amount"),
                    Args.optionalWeak(Args.catalogedElement(!"currency", Currency::class.java))
            )
        }
        val exchange = CommandSpec {
            executor(Commands::exchange)
            description(!"Exchange between units of currency.")
            arguments(
                    ItemVariantElement(!"item")
            )
        }
        CommandManager.register(Pieconomy.instance, pay, "pay")
        CommandManager.register(Pieconomy.instance, bal, "bal")
        CommandManager.register(Pieconomy.instance, deposit, "deposit")
        CommandManager.register(Pieconomy.instance, withdraw, "withdraw")
        CommandManager.register(Pieconomy.instance, transfer, "transfer")
        CommandManager.register(Pieconomy.instance, setbal, "setbal")
        CommandManager.register(Pieconomy.instance, exchange, "exchange")
        test {
            val t = CommandSpec {
                executor { src, args ->
                    src.sendMessage(!GameRegistry.getAllOf(Currency::class.java).joinToString())
                    src.sendMessage(!"\"${config.defaultCurrencyStr}\"")
                    src.sendMessage(!GameRegistry.getType(Currency::class.java, GameRegistry.getAllOf(Currency::class.java).first().id).unwrap().toString())
                    src.sendMessage(!config.defaultCurrency.toString())
                    CommandResult.success()
                }
            }
            CommandManager.register(Pieconomy.instance, t, "test")
        }
    }

    @Throws(CommandException::class)
    fun pay(src: CommandSource, args: CommandContext): CommandResult {
        if (src !is Player) throw CommandException(!"Must be a player!")
        val srcAcct = svc.getOrCreateAccount(src.uniqueId).get()
        val to = args.getOne<Account>("to").get()
        val currency = args.getOne<Currency>("currency").orElseGet { svc.defaultCurrency }
        val amount = args.getOne<BigDecimal>("amount").get()
        val res = srcAcct.transfer(to, currency, amount, CauseStackManager.currentCause) as PieconomyTransferResult
        when (res.result) {
            ResultType.ACCOUNT_NO_FUNDS -> throw CommandException(!"You do not have enough money!")
            ResultType.ACCOUNT_NO_SPACE -> throw CommandException(
                    if (res.payerFailed) {
                        !"You do not have enough space in your inventory to make change!"
                    } else {
                        if (to is PieconomyPlayerAccount) {
                            to.displayName + " does not have enough space in their inventory!"
                        } else {
                            to.displayName + " does not store this type of currency!"
                        }
                    }
            )
            ResultType.SUCCESS -> {
                src.sendMessage(!"Paid " + currency.format(amount) + " to " + to.displayName + ".")
                (to as? PieconomyPlayerAccount)?.id?.player()
                        ?.sendMessage(!"${src.name} paid you " + currency.format(amount) + ".")
                return CommandResult.success()
            }
            ResultType.CONTEXT_MISMATCH, ResultType.FAILED -> {
                src.sendMessage(!"An error occurred.")
                error("Unknown result type")
            }
        }
    }

    @Throws(CommandException::class)
    fun bal(src: CommandSource, args: CommandContext): CommandResult {
        val currency = args.getOne<Currency>("currency").orElseGet { svc.defaultCurrency }
        var self = false
        val acct = args.getOne<Account>("who").orElseGet {
            if (src is Player) {
                self = true
                svc.getOrCreateAccount(src.uniqueId).get()
            } else {
                throw CommandException(!"You must specify an account!")
            }
        }
        val bal = acct.getBalance(currency)
        src.sendMessage((if (self) !"You have " else acct.displayName + " has ") + currency.format(bal) + ".")
        return CommandResult.builder()
                .successCount(1)
                .queryResult(bal.toBigInteger().max(BigInteger.valueOf(Integer.MAX_VALUE.toLong())).toInt())
                .build()
    }

    @Throws(CommandException::class)
    fun deposit(src: CommandSource, args: CommandContext): CommandResult {
        val currency = args.getOne<Currency>("currency").orElseGet { svc.defaultCurrency }
        val amount = args.getOne<BigDecimal>("amount").get()
        val acct = args.getOne<Account>("to").get()
        if (acct is PieconomyServerAccount) args.checkPermission(src, "pieconomy.admin.deposit.server.${acct.name}")
        else args.checkPermission(src, "pieconomy.admin.deposit.player")
        val res = acct.deposit(currency, amount, CauseStackManager.currentCause)
        when (res.result!!) {
            ResultType.ACCOUNT_NO_SPACE ->
                if (acct is PieconomyPlayerAccount) {
                    throw CommandException(acct.displayName + " does not have enough space in their inventory!")
                } else {
                    throw CommandException(acct.displayName + " does not store this type of currency!")
                }
            ResultType.SUCCESS -> {
                src.sendMessage(!"Deposited " + currency.format(amount) + " in " + if (acct is PieconomyPlayerAccount) acct.displayName + "'s inventory" else acct.displayName + ".")
                (acct as? PieconomyPlayerAccount)?.id?.player()?.sendMessage(!"${src.name} has deposited " + currency.format(amount) + " in your inventory")
                return CommandResult.success()
            }
            ResultType.FAILED,
                    ResultType.CONTEXT_MISMATCH,
                    ResultType.ACCOUNT_NO_FUNDS -> {
                src.sendMessage(!"An error occurred.")
                error("Unknown result type")
            }
        }
    }

    @Throws(CommandException::class)
    fun withdraw(src: CommandSource, args: CommandContext): CommandResult {
        val currency = args.getOne<Currency>("currency").orElseGet { svc.defaultCurrency }
        val amount = args.getOne<BigDecimal>("amount").get()
        val acct = args.getOne<Account>("from").get()
        if (acct is PieconomyServerAccount) args.checkPermission(src, "pieconomy.admin.withdraw.server.${acct.name}")
        else args.checkPermission(src, "pieconomy.admin.withdraw.player")
        val res = acct.withdraw(currency, amount, CauseStackManager.currentCause)
        when (res.result!!) {
            ResultType.ACCOUNT_NO_FUNDS -> throw CommandException(acct.displayName + " does not have enough money!")
            ResultType.ACCOUNT_NO_SPACE ->
                if (acct is PieconomyPlayerAccount) {
                    throw CommandException(acct.displayName + " does not have space in their inventory for change!")
                } else {
                    throw CommandException(acct.displayName + " does not store this type of currency!")
                }
            ResultType.SUCCESS -> {
                src.sendMessage(!"Withdrew " + currency.format(amount) + " from " + acct.displayName + "'s inventory.")
                (acct as? PieconomyPlayerAccount)?.id?.player()?.sendMessage(!"${src.name} has withdrawn " + currency.format(amount) + " from your inventory.")
                return CommandResult.success()
            }
            ResultType.CONTEXT_MISMATCH,
                    ResultType.FAILED -> {
                src.sendMessage(!"An error occurred.")
                error("Unknown result type")
            }
        }
    }

    @Throws(CommandException::class)
    fun transfer(src: CommandSource, args: CommandContext): CommandResult {
        val currency = args.getOne<Currency>("currency").orElseGet { svc.defaultCurrency }
        val amount = args.getOne<BigDecimal>("amount").get()
        val from = args.getOne<Account>("from").get()
        val to = args.getOne<Account>("to").get()
        if (from is PieconomyServerAccount) args.checkPermission(src, "pieconomy.admin.transfer.from.server.${from.name}")
        else args.checkPermission(src, "pieconomy.admin.transfer.from.player")
        if (to is PieconomyServerAccount) args.checkPermission(src, "pieconomy.admin.transfer.to.server.${to.name}")
        else args.checkPermission(src, "pieconomy.admin.transfer.to.player")
        val res = from.transfer(to, currency, amount, CauseStackManager.currentCause)
                as PieconomyTransferResult
        when (res.result) {
            ResultType.ACCOUNT_NO_FUNDS -> throw CommandException(from.displayName + " does not have enough money!")
            ResultType.ACCOUNT_NO_SPACE -> throw CommandException(
                    if (res.payerFailed) {
                        from.displayName + " does not have space in their inventory for change!"
                    } else {
                        if (to is PieconomyPlayerAccount) {
                            to.displayName + " does not have enough space in their inventory!"
                        } else {
                            to.displayName + " does not store this type of currency!"
                        }
                    }
            )
            ResultType.SUCCESS -> {
                src.sendMessage(!"Transferred " + currency.format(amount) + " from " + from.displayName + " to " + to.displayName + ".")
                (from as? PieconomyPlayerAccount)?.id?.player()?.sendMessage(!"${src.name} has transferred " + currency.format(amount) + " from you to " + to.displayName + ".")
                (to as? PieconomyPlayerAccount)?.id?.player()?.sendMessage(!"${src.name} has transferred " + currency.format(amount) + " from " + from.displayName + " to you.")
                return CommandResult.success()
            }
            ResultType.FAILED,
                    ResultType.CONTEXT_MISMATCH -> {
                src.sendMessage(!"An error occurred.")
                error("Unknown result type")
            }
        }
    }

    @Throws(CommandException::class)
    fun setbal(src: CommandSource, args: CommandContext): CommandResult {
        val currency = args.getOne<Currency>("currency").orElseGet { svc.defaultCurrency }
        val amount = args.getOne<BigDecimal>("amount").get()
        val acct = args.getOne<Account>("who").get()
        if (acct is PieconomyServerAccount) args.checkPermission(src, "pieconomy.admin.setbal.server.${acct.name}")
        else args.checkPermission(src, "pieconomy.admin.setbal.player")
        val res = acct.setBalance(currency, amount, CauseStackManager.currentCause)
        when (res.result!!) {
            ResultType.ACCOUNT_NO_SPACE ->
                if (acct is PieconomyPlayerAccount) {
                    throw CommandException(acct.displayName + " does not have enough space in their inventory!")
                } else {
                    throw CommandException(acct.displayName + " does not store this type of currency!")
                }
            ResultType.SUCCESS -> {
                val bal = acct.getBalance(currency)
                src.sendMessage(!"Set " + acct.displayName + "'s balance to " + currency.format(bal) + ".")
                (acct as? PieconomyPlayerAccount)?.id?.player()?.sendMessage(!"${src.name} has set your balance to " + currency.format(bal) + ".")
                return CommandResult.success()
            }
            ResultType.FAILED,
                    ResultType.CONTEXT_MISMATCH -> {
                src.sendMessage(!"An error occurred.")
                error("Unknown result type")
            }
            ResultType.ACCOUNT_NO_FUNDS -> {
                src.sendMessage(!"An error occurred.")
                error("Unknown error regarding account balance subtraction calculations.")
            }
        }
    }

    @Throws(CommandException::class)
    fun exchange(src: CommandSource, args: CommandContext): CommandResult {
        if (src !is Player) {
            throw CommandException(!"You must be a player!")
        }
        val stack = src.getItemInHand(HandTypes.MAIN_HAND).orElseThrow { CommandException(!"You must hold the item!") }
        val convertFrom = ItemVariant(stack.type, stack.data)
        val currency = (config.items[convertFrom] ?: throw CommandException(!"Held item is not a currency item!")).currency
        if (currency is PieconomyCurrency && !currency.exchangeable) {
            throw CommandException(!"This currency is not exchangeable!")
        }
        val items = config.items.filter { (_, value) -> value.currency == currency }.mapValues { it.value.amount }
                .let { it.toSortedMap(Comparator.comparing<ItemVariant, BigDecimal> { v -> it[v] }.reversed()) }
        val convertTo = args.getOne<ItemVariant>("item").get()
        if (convertTo !in items) {
            throw CommandException(!"Requested item is not of the same currency!")
        }
        val valueFrom = items[convertFrom]!!
        val valueTo = items[convertTo]!!
        if (valueFrom > valueTo) {
            throw CommandException(!"This command is for up-converting, not down-converting.")
        }
        val min = items[items.lastKey()]!!
        when {
            (valueFrom - valueTo).abs() <= min -> {
                val converted = convertTo.toItem()
                converted.quantity = stack.quantity
                src.setItemInHand(HandTypes.MAIN_HAND, converted)
            }
            valueFrom < valueTo -> {
                val total = valueFrom * BigDecimal(stack.quantity)
                if (total.remainder(valueTo, MathContext.DECIMAL64) != BigDecimal.ZERO) {
                    throw CommandException(!"The quantities do not evenly divide!")
                }
                val quantityTo = total / valueTo
                val converted = convertTo.toItem()
                converted.quantity = quantityTo.toInt()
                src.setItemInHand(HandTypes.MAIN_HAND, converted)
            }
            else -> {
                val total = valueFrom * BigDecimal(stack.quantity)
                if (valueTo.remainder(total, MathContext.DECIMAL64) != BigDecimal.ZERO) {
                    throw CommandException(!"The quantities do not evenly divide!")
                }
                val quantityTo = valueTo / total
                val converted = convertTo.toItem()
                converted.quantity = quantityTo.toInt()
                src.setItemInHand(HandTypes.MAIN_HAND, converted)
            }
        }
        return CommandResult.success()
    }
}
