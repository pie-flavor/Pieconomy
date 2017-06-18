package flavor.pie.pieconomy

import co.aikar.timings.Timings
import com.google.common.collect.ImmutableList
import com.google.inject.Inject
import flavor.pie.kludge.*
import ninja.leaping.configurate.commented.CommentedConfigurationNode
import ninja.leaping.configurate.loader.ConfigurationLoader
import org.slf4j.Logger
import org.spongepowered.api.config.ConfigDir
import org.spongepowered.api.config.DefaultConfig
import org.spongepowered.api.data.DataContainer
import org.spongepowered.api.data.DataQuery
import org.spongepowered.api.data.DataView
import org.spongepowered.api.data.persistence.DataFormats
import org.spongepowered.api.event.Listener
import org.spongepowered.api.event.game.state.GameInitializationEvent
import org.spongepowered.api.event.game.state.GamePreInitializationEvent
import org.spongepowered.api.event.game.state.GameStoppingServerEvent
import org.spongepowered.api.plugin.Plugin
import org.spongepowered.api.service.economy.Currency
import org.spongepowered.api.service.economy.EconomyService
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@[Plugin(id = "pieconomy", name = "Pieconomy", version = "1.0-SNAPSHOT", authors = arrayOf("pie_flavor"),
        description = "An economy plugin that uses items as currency")]
class Pieconomy @[Inject] constructor(val logger: Logger,
                                      @[DefaultConfig(sharedRoot = false)] val loader: ConfigurationLoader<CommentedConfigurationNode>,
                                      @[DefaultConfig(sharedRoot = false)] val path: Path,
                                      @[ConfigDir(sharedRoot = false)] val dir: Path) {

    companion object {
        lateinit var instance: Pieconomy
    }

    init {
        Pieconomy.instance = this
    }

    val accts = dir.resolve("server_accounts.dat")!!
    lateinit var config: Config

    @[Listener]
    fun preInit(e: GamePreInitializationEvent) {
        if (!Files.exists(path)) {
            AssetManager.getAsset(this, "default.conf").get().copyToFile(path)
        }
        config = loader.load().getValue(Config.Companion.type)
        val set: MutableSet<Currency> = HashSet()
        config.currencies.forEach { (name, value) ->
            set += PieconomyCurrency(id = name, displayName = value.name, pluralDisplayName = value.plural,
                    defaultFractionDigits = value.decimalPlaces, format = value.format,
                    symbol = value.symbol, isDefault = config.defaultCurrencyStr == name)
        }
        val mod = PieconomyCurrencyRegistryModule(set)
        GameRegistry.registerModule(Currency::class.java, mod)
        val svc = PieconomyService()
        ServiceManager.setProvider(this, EconomyService::class.java, svc)

    }

    @[Listener]
    fun init(e: GameInitializationEvent) {
        Commands.register()
        if (config.serverAccounts.enable) {
            val svc: EconomyService by UncheckedService
            svc as PieconomyService
            val data = if (Files.exists(accts)) Files.newInputStream(accts).use { DataFormats.NBT.readFrom(it) } else null
            for (acctEntry in config.serverAccounts.accounts) {
                val acct = PieconomyServerAccount(acctEntry.name)
                if (data != null) {
                    val acctView = data.getView(DataQuery.of(acctEntry.id)).unwrap()
                    if (acctView != null) {
                        for (key in acctView.getKeys(false)) {
                            val currency = GameRegistry.getType(Currency::class.java, key.asString(':')).get()
                            val amount = BigDecimal(acctView.getString(key).get())
                            acct.money[currency] = amount
                        }
                    }
                }
                svc.serverAccounts[acctEntry.id] = acct
                if (acctEntry.currencies.type == ServerAccountCurrencyType.BLACKLIST) {
                    acct.currencies = ImmutableList.copyOf(svc.currencies.filter { it !in acctEntry.currencies.values })
                } else {
                    acct.currencies = ImmutableList.copyOf(svc.currencies.filter { it in acctEntry.currencies.values })
                }
                if (acctEntry.negativeValues.type == ServerAccountCurrencyType.BLACKLIST) {
                    acct.negativeValues = ImmutableList.copyOf(svc.currencies.filter { it !in acctEntry.negativeValues.values })
                } else {
                    acct.negativeValues = ImmutableList.copyOf(svc.currencies.filter { it in acctEntry.negativeValues.values })
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

    @[Listener]
    fun stop(e: GameStoppingServerEvent) {
        saveAccounts()
    }

    fun saveAccounts() {
        Timings.ofStart(this, "Save Server Accounts").use {
            val data = DataContainer.createNew(DataView.SafetyMode.NO_DATA_CLONED)
            val svc: EconomyService by UncheckedService
            svc as PieconomyService
            for ((id, acct) in svc.serverAccounts) {
                val acctView = data.createView(DataQuery.of(id))
                for ((currency, bal) in acct.money) {
                    acctView.set(DataQuery.of(currency.id), bal.toString())
                }
            }
            Files.newOutputStream(accts).use { DataFormats.NBT.writeTo(it, data) }
        }
    }

}