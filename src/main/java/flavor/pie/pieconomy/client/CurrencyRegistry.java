package flavor.pie.pieconomy.client;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class CurrencyRegistry {

    private static ImmutableMap<String, Currency> currencies = ImmutableMap.of();

    public static Currency getCurrencyById(String id) {
        return CurrencyRegistry.currencies.get(id);
    }

    public static ImmutableCollection<Currency> getAllCurrencies() {
        return CurrencyRegistry.currencies.values();
    }

    static void updateCurrencies(Map<String, Currency> currencies) {
        CurrencyRegistry.currencies = ImmutableMap.copyOf(currencies);
    }

}
