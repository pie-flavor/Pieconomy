package flavor.pie.pieconomy

import flavor.pie.kludge.*
import org.spongepowered.api.service.economy.Currency
import org.spongepowered.api.text.Text
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class PieconomyCurrency(
        private val symbol: Text, val format: BetterTextTemplate, private val id: String,
        private val isDefault: Boolean, private val displayName: Text,
        private val defaultFractionDigits: Int, private val pluralDisplayName: Text
) : Currency {
    override fun getSymbol(): Text = symbol

    override fun getName(): String = displayName.toPlain()

    override fun format(amount: BigDecimal, numFractionDigits: Int): Text =
            format.apply(mapOf("amount" to !(if (numFractionDigits == 0) amount.setScale(0, RoundingMode.HALF_UP) else amount.round(MathContext(numFractionDigits))).toPlainString(), "symbol" to symbol))

    override fun getId(): String = id

    override fun isDefault(): Boolean = isDefault

    override fun getDisplayName(): Text = displayName

    override fun getDefaultFractionDigits(): Int = defaultFractionDigits

    override fun getPluralDisplayName(): Text = pluralDisplayName

}
