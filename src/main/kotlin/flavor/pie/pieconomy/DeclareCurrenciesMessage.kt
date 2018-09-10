package flavor.pie.pieconomy

import org.spongepowered.api.network.ChannelBuf
import org.spongepowered.api.network.Message
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.serializer.TextSerializers
import java.math.BigDecimal

class DeclareCurrenciesMessage(var currencies: Collection<PieconomyCurrency>): Message {

    constructor(): this(listOf())

    override fun readFrom(buf: ChannelBuf) {
        //clientbound
    }

    override fun writeTo(buf: ChannelBuf) {
        buf.writeInteger(currencies.size)
        for (currency in currencies) {
            buf.writeCurrency(currency)
        }
    }

    private fun ChannelBuf.writeCurrency(currency: PieconomyCurrency) {
        writeUTF(currency.id)
        writeInteger(currency.defaultFractionDigits)
        writeText(currency.format.apply(mapOf("symbol" to currency.symbol)))
        writeItemVariantToBigDecimalMap(getItemVariantToValueMap(currency))
    }

    private fun ChannelBuf.writeText(text: Text) {
        writeUTF(TextSerializers.JSON.serialize(text))
    }

    private fun ChannelBuf.writeItemVariantToBigDecimalMap(map: Map<ItemVariant, BigDecimal>) {
        writeInteger(map.size)
        for ((key, value) in map) {
            writeItemVariant(key)
            writeBigDecimal(value)
        }
    }

    private fun getItemVariantToValueMap(currency: PieconomyCurrency): Map<ItemVariant, BigDecimal> {
        return config.items.filter { it.value.currency == currency }.mapValues { it.value.amount }
    }

    private fun ChannelBuf.writeItemVariant(variant: ItemVariant) {
        writeUTF(variant.type.id)
        writeInteger(variant.data)
    }

    private fun ChannelBuf.writeBigDecimal(value: BigDecimal) {
        writeInteger(value.scale())
        val bytes = value.unscaledValue().toByteArray()
        writeBytes(bytes)
    }

}