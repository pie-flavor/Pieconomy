package flavor.pie.pieconomy

import com.google.common.reflect.TypeToken
import flavor.pie.kludge.*
import ninja.leaping.configurate.ConfigurationNode
import ninja.leaping.configurate.objectmapping.ObjectMappingException
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializer
import org.spongepowered.api.CatalogTypes
import org.spongepowered.api.item.ItemType
import org.spongepowered.api.item.inventory.ItemStack
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.TextRepresentable
import org.spongepowered.api.util.TypeTokens
import java.math.BigDecimal

class BigDecimalSerializer : TypeSerializer<BigDecimal> {
    override fun serialize(type: TypeToken<*>, obj: BigDecimal, value: ConfigurationNode) {
        value.value = obj.toPlainString()
    }

    override fun deserialize(type: TypeToken<*>, value: ConfigurationNode): BigDecimal {
        return BigDecimal(value.string)
    }
}

val AT_SIGN_REGEX = "@".toRegex()

class ItemVariantSerializer : TypeSerializer<ItemVariant> {
    override fun deserialize(type: TypeToken<*>, value: ConfigurationNode): ItemVariant {
        return value.string.split(AT_SIGN_REGEX).let { ItemVariant(
                GameRegistry.getType(CatalogTypes.ITEM_TYPE, it[0]).orElseThrow { ObjectMappingException("Invalid ItemType") },
                if (it.size > 1) it[1].toInt() else null) }
    }

    override fun serialize(type: TypeToken<*>, obj: ItemVariant, value: ConfigurationNode) {
        value.value = obj.type.id.let { if (obj.data == null) it else "$it@${obj.data}" }
    }

}

data class ItemVariant(val type: ItemType, val data: Int?) {
    fun toItem(): ItemStack {
        if (data == null) {
            return ItemStack.of(type, 1)
        } else {
            return ItemStack.of(type, 1).withData(data)
        }
    }
}

class BetterTextTemplateSerializer : TypeSerializer<BetterTextTemplate> {
    override fun deserialize(type: TypeToken<*>, value: ConfigurationNode): BetterTextTemplate {
        return BetterTextTemplate(value.getValue(TypeTokens.TEXT_TOKEN))
    }

    override fun serialize(type: TypeToken<*>, obj: BetterTextTemplate, value: ConfigurationNode) {
        value.setValue(TypeTokens.TEXT_TOKEN, obj.template)
    }
}

class BetterTextTemplate(val template: Text) {
    companion object {
        val type: TypeToken<BetterTextTemplate> = TypeToken.of(BetterTextTemplate::class.java)
    }

    fun apply(map: Map<String, *>): Text {
        var text = template
        for ((key, value) in map) {
            if (value is TextRepresentable) {
                text = text.replace("%{$key}%", value.toText())
            } else {
                text = text.replace("%{$key}%", !value.toString())
            }
        }
        return text
    }
}
