package flavor.pie.pieconomy

import com.google.common.collect.ImmutableList
import flavor.pie.kludge.*
import org.spongepowered.api.command.CommandSource
import org.spongepowered.api.command.args.ArgumentParseException
import org.spongepowered.api.command.args.CommandArgs
import org.spongepowered.api.command.args.CommandContext
import org.spongepowered.api.command.args.CommandElement
import org.spongepowered.api.command.args.GenericArguments
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.service.economy.EconomyService
import org.spongepowered.api.text.Text

class AccountElement(key: Text, val type: Type = Type.BOTH) : CommandElement(key) {

    enum class Type {
        SERVER, PLAYER, BOTH
    }

    private val playerElement = GenericArguments.player(key)

    @[Throws(ArgumentParseException::class)]
    override fun parseValue(source: CommandSource, args: CommandArgs): Any {
        val svc: EconomyService by UncheckedService
        svc as PieconomyService
        when (type) {
            Type.BOTH -> {
                if (args.peek().startsWith("server:", true)) {
                    val name = args.next().substring(7)
                    return svc.serverAccounts.values.find { it.name == name } ?: throw args.createError(!"Invalid server account")
                } else if (args.peek().startsWith("player:")) {
                    val name = args.next().substring(7)
                    args.insertArg(name)
                    val context = CommandContext()
                    playerElement.parse(source, args, context)
                    return svc.getOrCreateAccount(context.getOne<Player>(key).get().uniqueId).get()
                }
                val state = args.state
                try {
                    val context = CommandContext()
                    playerElement.parse(source, args, context)
                    return svc.getOrCreateAccount(context.getOne<Player>(key).get().uniqueId).get()
                } catch (e: ArgumentParseException) {
                    args.state = state
                    val next = args.next()
                    return svc.serverAccounts.values.find { it.name == next } ?: throw args.createError(!"Invalid account name")
                }
            }
            Type.PLAYER -> {
                if (args.peek().startsWith("player:")) {
                    val state = args.state
                    val name = args.next().substring(7)
                    args.removeArgs(state, args.state)
                    args.insertArg(name)
                }
                val context = CommandContext()
                playerElement.parse(source, args, context)
                return svc.getOrCreateAccount(context.getOne<Player>(key).get().uniqueId).get()
            }
            Type.SERVER -> {
                val next = args.next().let { if (it.startsWith("server:")) it.substring(7) else it }
                return svc.serverAccounts.values.find { it.name == next } ?: throw args.createError(!"Invalid server account")
            }
        }
    }

    override fun complete(src: CommandSource, args: CommandArgs, context: CommandContext): List<String> {
        val svc: EconomyService by UncheckedService
        svc as PieconomyService
        val next = args.nextIfPresent().orElse("")
        val builder = ImmutableList.builder<String>()
        if (type != Type.SERVER) {
            for (player in Server.onlinePlayers) {
                if (next == "" || player.name.startsWith(next, ignoreCase = true)) {
                    if (player.name in svc.serverAccounts.values.map { it.name }) {
                        builder.add("player:${player.name}")
                    } else {
                        builder.add(player.name)
                    }
                } else if ("player:${player.name}".startsWith(next, ignoreCase = true)) {
                    builder.add("player:${player.name}")
                }
            }
        }
        if (type != Type.PLAYER) {
            for (acct in svc.serverAccounts.values) {
                if (next == "" || acct.name.startsWith(next, ignoreCase = true)) {
                    if (acct.name in Server.onlinePlayers.map { it.name }) {
                        builder.add("server:${acct.name}")
                    } else {
                        builder.add(acct.name)
                    }
                } else if ("server:${acct.name}".startsWith(next, ignoreCase = true)) {
                    builder.add("server:${acct.name}")
                }
            }
        }
        return builder.build()
    }
}