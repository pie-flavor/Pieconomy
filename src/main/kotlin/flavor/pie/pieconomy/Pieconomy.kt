@file:Suppress("UNUSED_PARAMETER")

package flavor.pie.pieconomy

import co.aikar.timings.Timings
import com.google.common.collect.ImmutableList
import com.google.common.reflect.TypeToken
import com.google.inject.Inject
import flavor.pie.kludge.*
import ninja.leaping.configurate.commented.CommentedConfigurationNode
import ninja.leaping.configurate.loader.ConfigurationLoader
import org.bstats.sponge.MetricsLite2
import org.slf4j.Logger
import org.spongepowered.api.config.ConfigDir
import org.spongepowered.api.config.DefaultConfig
import org.spongepowered.api.data.DataContainer
import org.spongepowered.api.data.DataQuery
import org.spongepowered.api.data.DataView
import org.spongepowered.api.data.persistence.DataFormats
import org.spongepowered.api.event.Listener
import org.spongepowered.api.event.game.state.GameInitializationEvent
import org.spongepowered.api.event.game.state.GamePostInitializationEvent
import org.spongepowered.api.event.game.state.GameStoppingServerEvent
import org.spongepowered.api.event.network.ClientConnectionEvent
import org.spongepowered.api.item.inventory.Inventory
import org.spongepowered.api.item.inventory.ItemStack
import org.spongepowered.api.network.ChannelBinding
import org.spongepowered.api.plugin.Plugin
import org.spongepowered.api.service.economy.Currency
import org.spongepowered.api.service.economy.EconomyService
import org.spongepowered.api.util.TypeTokens
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Plugin(id = "pieconomy", name = "Pieconomy", version = "0.6.2-SNAPSHOT", authors = ["pie_flavor"],
        description = "An economy plugin that uses items as currency")
class Pieconomy @Inject constructor(val logger: Logger,
                                      @DefaultConfig(sharedRoot = false) val loader: ConfigurationLoader<CommentedConfigurationNode>,
                                      @DefaultConfig(sharedRoot = false) val path: Path,
                                      @ConfigDir(sharedRoot = false) val dir: Path,
                                      val metrics: MetricsLite2) {

    companion object {
        lateinit var instance: Pieconomy
    }

    init {
        Pieconomy.instance = this
    }

    val accts = dir.resolve("server_accounts.dat")!!
    lateinit var config: Config
    lateinit var svc: PieconomyService
    lateinit var channel: ChannelBinding.RawDataChannel

    @Listener
    fun init(e: GameInitializationEvent) {
        if (!Files.exists(path)) {
            AssetManager.getAsset(this, "default.conf").get().copyToFile(path)
        }
        var opts = loader.defaultOptions
        val serializers = opts.serializers.newChild()
        serializers.registerType(TypeToken.of(BigDecimal::class.java), BigDecimalSerializer)
        serializers.registerType(TypeToken.of(ItemVariant::class.java), ItemVariantSerializer)
        serializers.registerType(BetterTextTemplate.type, BetterTextTemplateSerializer)
        opts = opts.setSerializers(serializers)
        val node = loader.load(opts)
        if (node.getNode("version").int < 2) {
            logger.info("Upgrading old config to version 2")
            for (currNode in node.getNode("currencies").childrenMap.values) {
                currNode.getNode("format").let {
                    currNode.getNode("format-old").run {
                        value = it
                        setComment("Migrate to 'format'!")
                    }
                    it.setValue(TypeTokens.TEXT_TOKEN, !"%{amount}% %{symbol}%")
                }
            }
            node.getNode("version").value = 2
            loader.save(node)
        } else {
            if (!node.getNode("format-old").isVirtual) {
                logger.warn("Legacy text format detected in config.")
                logger.warn("Please migrate 'format-old' to 'format', and then delete 'format-old'.")
            }
        }
        if (node.getNode("version").int < 3) {
            logger.info("Upgrading old config to version 3")
            for (currNode in node.getNode("currencies").childrenMap.values) {
                currNode.getNode("exchangeable").value = true
            }
            node.getNode("version").value = 3
            loader.save(node)
        }
        config = node.getValue(Config.type)!!
        val set: MutableSet<Currency> = HashSet()
        config.currencies.forEach { (id, value) ->
            set += PieconomyCurrency(id = id, displayName = value.name, pluralDisplayName = value.plural,
                    defaultFractionDigits = value.decimalPlaces, format = value.format,
                    symbol = value.symbol, isDefault = config.defaultCurrencyStr == id, exchangeable = value.exchangeable)
        }
        val mod = PieconomyCurrencyRegistryModule(set)
        GameRegistry.registerModule(Currency::class.java, mod)
        svc = PieconomyService()
        ServiceManager.setProvider(this, EconomyService::class.java, svc)
        Commands.register()
        channel = ChannelRegistrar.createRawChannel(this, "pieconomy")
    }

    @Listener
    fun postInit(e: GamePostInitializationEvent) {
        if (config.serverAccounts.enable) {
            val data = if (Files.exists(accts)) Files.newInputStream(accts).use { DataFormats.NBT.readFrom(it) } else null
            for (acctEntry in config.serverAccounts.accounts) {
                val currencies = ImmutableList.copyOf(svc.currencies.filter(
                        if (acctEntry.currencies.type == ServerAccountCurrencyType.BLACKLIST) {
                            { c -> c !in acctEntry.currencies.values }
                        } else {
                            { c -> c in acctEntry.currencies.values }
                        }
                ))
                val negativeValues = ImmutableList.copyOf(svc.currencies.filter(
                        if (acctEntry.negativeValues.type == ServerAccountCurrencyType.BLACKLIST) {
                            { c -> c !in acctEntry.negativeValues.values }
                        } else {
                            { c -> c in acctEntry.negativeValues.values }
                        }
                ))
                val acct = PieconomyServerAccount(acctEntry.name, currencies, negativeValues)
                if (data != null) {
                    val acctView = data.getView(DataQuery.of(acctEntry.id)).unwrap()
                    if (acctView != null) {
                        for (key in acctView.getKeys(false)) {
                            val currency = GameRegistry.getType(Currency::class.java, key.asString(':')).unwrap()
                            val amount = acctView.getString(key).unwrap()?.let { BigDecimal(it) }
                            currency?.let { acct.money[it] = amount ?: BigDecimal.ZERO }
                        }
                    }
                }
                svc.serverAccounts[acctEntry.id] = acct

            }
            if (data != null && config.serverAccounts.dynamicAccounts.enable) {
                val alreadyLoaded = config.serverAccounts.accounts.map { it.id }
                for (acctKey in data.getKeys(false)) {
                    val acctId = acctKey.asString(':')
                    if (acctId !in alreadyLoaded) {
                        val currencies = ImmutableList.copyOf(svc.currencies.filter(
                                if (config.serverAccounts.dynamicAccounts.currencies.type == ServerAccountCurrencyType.BLACKLIST) {
                                    { c -> c !in config.serverAccounts.dynamicAccounts.currencies.values }
                                } else {
                                    { c -> c in config.serverAccounts.dynamicAccounts.currencies.values }
                                }
                        ))
                        val negativeValues = ImmutableList.copyOf(svc.currencies.filter(
                                if (config.serverAccounts.dynamicAccounts.negativeValues.type == ServerAccountCurrencyType.BLACKLIST) {
                                    { c -> c !in config.serverAccounts.dynamicAccounts.negativeValues.values }
                                } else {
                                    { c -> c in config.serverAccounts.dynamicAccounts.negativeValues.values }
                                }
                        ))
                        val acct = PieconomyServerAccount(acctId, currencies, negativeValues)
                        val acctView = data.getView(acctKey).unwrap()
                        if (acctView != null) {
                            for (key in acctView.getKeys(false)) {
                                val currency = GameRegistry.getType(Currency::class.java, key.asString(':')).unwrap()
                                val amount = acctView.getString(key).unwrap()?.let { BigDecimal(it) }
                                currency?.let { acct.money[it] = amount ?: BigDecimal.ZERO }
                            }
                        }
                        svc.serverAccounts[acctId] = acct
                    }
                }
            }
            if (config.serverAccounts.autosaveInterval > 0) {
                Task(this) {
                    execute(this@Pieconomy::saveAccounts)
                    delay(config.serverAccounts.autosaveInterval, TimeUnit.MINUTES)
                    interval(config.serverAccounts.autosaveInterval, TimeUnit.MINUTES)
                    name("Pieconomy-S-ServerAccountAutosave")
                }
            }
        }
    }

    @Listener
    fun stop(e: GameStoppingServerEvent) {
        saveAccounts()
    }

    @Listener
    fun join(e: ClientConnectionEvent.Join) {
        Task(this) {
            delayTicks(1)
            execute { _ ->
                channel.sendTo(e.targetEntity) { buf ->
                    buf.writeInteger(0)
                    DeclareCurrenciesMessage(svc.currencies.mapNotNull { it as? PieconomyCurrency }).writeTo(buf)
                }
            }
        }
    }

    fun saveAccounts() {
        Timings.ofStart(this, "Save Server Accounts").use { _ ->
            val data = DataContainer.createNew(DataView.SafetyMode.NO_DATA_CLONED)
            val svc: EconomyService by UncheckedService
            for ((id, acct) in (svc as PieconomyService).serverAccounts) {
                val acctView = data.createView(DataQuery.of(id))
                for ((currency, bal) in acct.money) {
                    acctView.set(DataQuery.of(currency.id), bal.toString())
                }
            }
            Files.newOutputStream(accts).use { DataFormats.NBT.writeTo(it, data) }
        }
    }

}

val dataValueQuery: DataQuery = DataQuery.of("UnsafeDamage")
val ItemStack.data: Int
    get() = this.toContainer().getInt(dataValueQuery).unwrap() ?: 0

fun ItemStack.withData(data: Int): ItemStack {
    val container = this.toContainer().set(dataValueQuery, data)
    return ItemStack { fromContainer(container) }
}

@Suppress("UNCHECKED_CAST")
operator fun <T: Inventory> Inventory.get(variant: ItemVariant): T {
    return this[ItemStack.of(variant.type, 1).withData(variant.data)] as T
}

fun debug(s: String) {
    Pieconomy.instance.logger.debug(s)
}

inline fun test(x: () -> Unit) {

}