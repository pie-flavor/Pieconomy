package flavor.pie.pieconomy

import com.google.common.reflect.TypeToken
import flavor.pie.kludge.*
import ninja.leaping.configurate.ConfigurationNode
import ninja.leaping.configurate.objectmapping.ObjectMappingException
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializer
import org.checkerframework.checker.nullness.qual.NonNull
import org.checkerframework.checker.nullness.qual.Nullable
import org.spongepowered.api.CatalogTypes
import org.spongepowered.api.item.ItemType
import org.spongepowered.api.item.inventory.ItemStack
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.TextRepresentable
import org.spongepowered.api.util.TypeTokens
import java.math.BigDecimal

object BigDecimalSerializer : TypeSerializer<BigDecimal> {
    override fun serialize(type: @NonNull TypeToken<*>, obj: @Nullable BigDecimal?, value: @NonNull ConfigurationNode) {
        value.value = obj!!.toPlainString()
    }

    override fun deserialize(type: TypeToken<*>, value: ConfigurationNode): BigDecimal {
        return BigDecimal(value.string!!)
    }
}

object ItemVariantSerializer : TypeSerializer<ItemVariant> {
    override fun deserialize(type: TypeToken<*>, value: ConfigurationNode): ItemVariant {
        try {
            return ItemVariant.fromString(value.string!!)
        } catch (e: IllegalArgumentException) {
            throw ObjectMappingException(e)
        }
    }

    override fun serialize(
        type: @NonNull TypeToken<*>,
        obj: @Nullable ItemVariant?,
        value: @NonNull ConfigurationNode
    ) {
        value.value = obj!!.toString()
    }

}

data class ItemVariant(val type: ItemType, val data: Int) {
    companion object {
        fun fromItem(stack: ItemStack): ItemVariant = ItemVariant(stack.type, stack.data)
        fun fromString(str: String): ItemVariant = str.split("@").let { ItemVariant(
                GameRegistry.getType(CatalogTypes.ITEM_TYPE, it[0]).orElseThrow { IllegalArgumentException("Invalid ItemType") },
                if (it.size > 1) it[1].toInt() else 0) }
    }
    fun toItem(): ItemStack {
        return if (data == 0) {
            ItemStack.of(type, 1)
        } else {
            ItemStack.of(type, 1).withData(data)
        }
    }

    override fun toString(): String {
        return type.id.let { if (data == 0) it else "$it@$data" }
    }
}

object BetterTextTemplateSerializer : TypeSerializer<BetterTextTemplate> {
    override fun deserialize(type: TypeToken<*>, value: ConfigurationNode): BetterTextTemplate {
        return BetterTextTemplate(value.getValue(TypeTokens.TEXT_TOKEN)!!)
    }

    override fun serialize(
        type: @NonNull TypeToken<*>,
        obj: @Nullable BetterTextTemplate?,
        value: @NonNull ConfigurationNode
    ) {
        value.setValue(TypeTokens.TEXT_TOKEN, obj!!.template)
    }
}

class BetterTextTemplate(val template: Text) {
    companion object {
        val type: TypeToken<BetterTextTemplate> = TypeToken.of(BetterTextTemplate::class.java)
    }

    fun apply(map: Map<String, *>): Text {
        var text = template
        for ((key, value) in map) {
            text = if (value is TextRepresentable) {
                text.replace("%{$key}%", value.toText())
            } else {
                text.replace("%{$key}%", !value.toString())
            }
        }
        return text
    }
}
