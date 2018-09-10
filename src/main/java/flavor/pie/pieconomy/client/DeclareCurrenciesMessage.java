package flavor.pie.pieconomy.client;

import com.google.common.collect.ImmutableMap;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

public class DeclareCurrenciesMessage implements IMessage {

    private ImmutableMap<String, Currency> currencies;

    public DeclareCurrenciesMessage() {}

    public DeclareCurrenciesMessage(Map<String, Currency> currencies) {
        this.currencies = ImmutableMap.copyOf(currencies);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ImmutableMap.Builder<String, Currency> builder = ImmutableMap.builder();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            Currency currency = readCurrency(buf);
            builder.put(currency.getId(), currency);
        }
        this.currencies = builder.build();
    }

    private Currency readCurrency(ByteBuf buf) {
        String id = ByteBufUtils.readUTF8String(buf);
        int decimalPlaces = buf.readInt();
        ITextComponent format = this.readITextComponent(buf);
        ImmutableMap<ItemState, BigDecimal> values = this.readItemStateToBigDecimalMap(buf);
        return new Currency(id, decimalPlaces, format, values);
    }

    private ImmutableMap<ItemState, BigDecimal> readItemStateToBigDecimalMap(ByteBuf buf) {
        ImmutableMap.Builder<ItemState, BigDecimal> map = ImmutableMap.builder();
        int count = buf.readInt();
        for (int i = 0; i < count; i++) {
            String itemId = ByteBufUtils.readUTF8String(buf);
            Item item = Item.getByNameOrId(itemId);
            if (item == null) {
                continue;
            }
            int state = buf.readInt();
            BigDecimal decimal = this.readBigDecimal(buf);
            ItemState istate = new ItemState(item, state);
            map.put(istate, decimal);
        }
        return map.build();
    }

    private BigDecimal readBigDecimal(ByteBuf buf) {
        BigInteger integer = this.readBigInteger(buf);
        int scale = buf.readInt();
        return new BigDecimal(integer, scale);
    }

    private BigInteger readBigInteger(ByteBuf buf) {
        int len = buf.readInt();
        byte[] dst = new byte[len];
        buf.readBytes(dst);
        return new BigInteger(dst);
    }

    private ITextComponent readITextComponent(ByteBuf buf) {
        return ITextComponent.Serializer.jsonToComponent(ByteBufUtils.readUTF8String(buf));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        //clientbound
    }

    public static class Handler implements IMessageHandler<DeclareCurrenciesMessage, IMessage> {

        @Override
        public IMessage onMessage(DeclareCurrenciesMessage message, MessageContext ctx) {
            ImmutableMap<String, Currency> map = message.currencies;
            Minecraft.getMinecraft().addScheduledTask(() -> CurrencyRegistry.updateCurrencies(map));
            return null;
        }

    }

}
