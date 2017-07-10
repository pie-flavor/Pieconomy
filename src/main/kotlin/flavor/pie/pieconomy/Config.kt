package flavor.pie.pieconomy

import com.google.common.reflect.TypeToken
import com.google.common.reflect.TypeToken.of
import flavor.pie.kludge.*
import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable
import org.spongepowered.api.CatalogType
import org.spongepowered.api.item.ItemType
import org.spongepowered.api.service.economy.Currency
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.TextTemplate
import java.math.BigDecimal

val config get() = Pieconomy.instance.config
operator fun Map<ItemVariant, ItemEntry>.get(type: ItemType, data: Int): ItemEntry? = keys
                .firstOrNull { it.type == type && (it.data == null || it.data == data) }
                ?.let { this[it] }

@[ConfigSerializable] class Config {
    companion object {
        val type: TypeToken<Config> = of(Config::class.java)
    }
    @[Setting] var items: Map<ItemVariant, ItemEntry> = emptyMap()
    @[Setting] var currencies: Map<String, CurrencyEntry> = emptyMap()
    @[Setting("default-currency")] lateinit var defaultCurrencyStr: String
    var defaultCurrency: Currency
        get() = GameRegistry.getType(Currency::class.java, defaultCurrencyStr).get()
        set(value) { defaultCurrencyStr = value.id }
    @[Setting] var version: Int = 1
    @[Setting("server-accounts")] var serverAccounts: ServerAccountSection = ServerAccountSection()
}

@[ConfigSerializable] class ItemEntry {
    @[Setting("currency")] private lateinit var currencyStr: String
    var currency: Currency
        get() = GameRegistry.getType(Currency::class.java, currencyStr).get()
        set(value) { currencyStr = value.id }
    @[Setting] var amount: BigDecimal = BigDecimal.ONE
}

@[ConfigSerializable] class CurrencyEntry {
    @[Setting("decimal-places")] var decimalPlaces = 0
    @[Setting] lateinit var name: Text
    @[Setting] lateinit var plural: Text
    @[Setting] lateinit var symbol: Text
    @[Setting] lateinit var format: TextTemplate
}

@[ConfigSerializable] class ServerAccountSection {
    @[Setting] var enable: Boolean = false
    @[Setting("autosave-interval")] var autosaveInterval: Long = 20
    @[Setting] var accounts: List<ServerAccountEntry> = emptyList()
}

@[ConfigSerializable] class ServerAccountEntry {
    @[Setting] lateinit var name: String
    @[Setting] lateinit var id: String
    @[Setting] var currencies: ServerAccountCurrencyEntry = ServerAccountCurrencyEntry().also { it.type = ServerAccountCurrencyType.BLACKLIST }
    @[Setting("negative-values")] var negativeValues: ServerAccountCurrencyEntry = ServerAccountCurrencyEntry().also { it.type = ServerAccountCurrencyType.WHITELIST }
}

@[ConfigSerializable] class ServerAccountCurrencyEntry {
    @[Setting] lateinit var type: ServerAccountCurrencyType
    @[Setting("values")] private var valueStrs: List<String> = emptyList()
    var values: List<Currency>
        get() = valueStrs.map { GameRegistry.getType(Currency::class.java, it).get() }
        set(value) { valueStrs = value.map(CatalogType::getId) }
}

enum class ServerAccountCurrencyType {
    BLACKLIST, WHITELIST;
}