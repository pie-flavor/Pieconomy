package flavor.pie.pieconomy

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.google.common.collect.ImmutableList
import flavor.pie.kludge.*
import org.spongepowered.api.service.context.ContextCalculator
import org.spongepowered.api.service.economy.Currency
import org.spongepowered.api.service.economy.EconomyService
import org.spongepowered.api.service.economy.account.Account
import org.spongepowered.api.service.economy.account.UniqueAccount
import java.util.Optional
import java.util.UUID
import java.util.concurrent.TimeUnit

class PieconomyService : EconomyService {
    val cache: LoadingCache<UUID, PieconomyPlayerAccount> = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES).build(::PieconomyPlayerAccount)

    val serverAccounts: MutableMap<String, PieconomyServerAccount> = HashMap()

    override fun getOrCreateAccount(uuid: UUID): Optional<UniqueAccount> = cache[uuid].optional

    override fun getOrCreateAccount(identifier: String): Optional<Account> =
            (serverAccounts.values.firstOrNull { it.name == identifier } ?: if (config.serverAccounts.dynamicAccounts.enable) {
                PieconomyServerAccount(identifier,
                        currencies = ImmutableList.copyOf(currencies.filter(
                                if (config.serverAccounts.dynamicAccounts.currencies.type == ServerAccountCurrencyType.BLACKLIST) {
                                    { c -> c !in config.serverAccounts.dynamicAccounts.currencies.values }
                                } else {
                                    { c -> c in config.serverAccounts.dynamicAccounts.currencies.values }
                                }
                        )),
                        negativeValues = ImmutableList.copyOf(currencies.filter(
                                if (config.serverAccounts.dynamicAccounts.negativeValues.type == ServerAccountCurrencyType.BLACKLIST) {
                                    { c -> c !in config.serverAccounts.dynamicAccounts.negativeValues.values }
                                } else {
                                    { c -> c in config.serverAccounts.dynamicAccounts.negativeValues.values }
                                }
                        )))
            } else {
                null
            }).optional

    override fun registerContextCalculator(calculator: ContextCalculator<Account>?) {}

    override fun getDefaultCurrency(): Currency = config.defaultCurrency

    override fun getCurrencies(): Set<Currency> = GameRegistry.getAllOf(Currency::class.java).toSet()

    override fun hasAccount(uuid: UUID): Boolean = uuid.user() != null

    override fun hasAccount(identifier: String): Boolean = serverAccounts.values.any { it.name == identifier }

}
