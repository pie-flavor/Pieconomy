package flavor.pie.pieconomy.client;

import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Arrays;

public class PieconomyEventListener {

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent e) {
        ItemStack stack = e.getItemStack();
        ItemState state = new ItemState(stack.getItem(), stack.getItemDamage());
        for (Currency currency : CurrencyRegistry.getAllCurrencies()) {
            BigDecimal value = currency.getValues().get(state);
            if (value != null) {
                ITextComponent format = currency.getFormat();
                char[] dfSize = new char[currency.getDecimalPlaces()];
                Arrays.fill(dfSize, '0');
                DecimalFormat df = new DecimalFormat("#,##0." + new String(dfSize));
                ITextComponent show = Util.replace(format, "%{amount}%", new TextComponentString(df.format(value)));
                BigDecimal total = BigDecimal.valueOf(stack.getCount()).multiply(value);
                ITextComponent totalShow =
                        Util.replace(format, "%{amount}%", new TextComponentString(df.format(total)));
                ITextComponent tooltipText = new TextComponentString("")
                        .appendText("Worth ")
                        .appendSibling(totalShow)
                        .appendText(" (")
                        .appendSibling(show)
                        .appendText(" each)");
                e.getToolTip().add(tooltipText.getFormattedText());
            }
        }
    }

}
