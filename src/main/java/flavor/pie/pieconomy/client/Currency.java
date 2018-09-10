package flavor.pie.pieconomy.client;

import com.google.common.collect.ImmutableMap;
import net.minecraft.util.text.ITextComponent;

import java.math.BigDecimal;
import java.util.Map;

public class Currency {
    private String id;
    private int decimalPlaces;
    private ITextComponent format;
    private ImmutableMap<ItemState, BigDecimal> values;

    public Currency(String id, int decimalPlaces, ITextComponent format, Map<ItemState, BigDecimal> values) {
        this.id = id;
        this.decimalPlaces = decimalPlaces;
        this.format = format;
        this.values = ImmutableMap.copyOf(values);
    }

    public String getId() {
        return id;
    }

    public int getDecimalPlaces() {
        return decimalPlaces;
    }

    public ITextComponent getFormat() {
        return format;
    }

    public ImmutableMap<ItemState, BigDecimal> getValues() {
        return values;
    }
}
