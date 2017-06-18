package flavor.pie.pieconomy

import com.google.common.collect.HashBiMap
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import flavor.pie.kludge.*
import org.spongepowered.api.registry.AdditionalCatalogRegistryModule
import org.spongepowered.api.registry.RegistrationPhase
import org.spongepowered.api.registry.util.DelayedRegistration
import org.spongepowered.api.service.economy.Currency
import java.util.Optional

class PieconomyCurrencyRegistryModule(defaults: Set<Currency>) : AdditionalCatalogRegistryModule<Currency> {
    val defaults: Set<Currency>
    val currencies: MutableMap<String, Currency> = HashBiMap.create()

    init {
        this.defaults = ImmutableSet.copyOf(defaults)
    }

    override fun getById(id: String): Optional<Currency> = currencies[id].optional

    override fun getAll(): Collection<Currency> = ImmutableList.copyOf(currencies.values)

    override fun registerAdditionalCatalog(extraCatalog: Currency) {
        currencies[extraCatalog.id] = extraCatalog
    }

    @[DelayedRegistration(RegistrationPhase.INIT)]
    override fun registerDefaults() {
        defaults.forEach { currencies[it.id] = it }
    }

}