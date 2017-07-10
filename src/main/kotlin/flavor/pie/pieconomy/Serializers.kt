package flavor.pie.pieconomy

import com.google.common.reflect.TypeToken
import flavor.pie.kludge.*
import ninja.leaping.configurate.ConfigurationNode
import ninja.leaping.configurate.objectmapping.ObjectMappingException
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializer
import org.spongepowered.api.CatalogTypes
import org.spongepowered.api.item.ItemType
import org.spongepowered.api.item.inventory.ItemStack
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
            return ItemStack.of(type, 1);
        } else {
            return ItemStack.of(type, 1).withData(data)
        }
    }
}
