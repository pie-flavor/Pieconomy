package flavor.pie.pieconomy.client;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Util {

    public static ITextComponent replace(ITextComponent src, String oldValue, ITextComponent newValue) {
        List<ITextComponent> newSiblings = src.getSiblings();
        if (!src.getSiblings().isEmpty()) {
            newSiblings = new ArrayList<>();
            for (ITextComponent component : src.getSiblings()) {
                newSiblings.add(replace(component, oldValue, newValue));
            }
        }
        String plain = src.getUnformattedComponentText();
        if (!plain.contains(oldValue)) {
            return src;
        }
        if (plain.equals(oldValue)) {
            return newValue.createCopy();
        }
        TextComponentString string = new TextComponentString("");
        String[] parts = plain.split(Pattern.quote(oldValue), -2);
        for (int i = 0; i < parts.length - 1; i++) {
            string.appendSibling(new TextComponentString(parts[i]));
            string.appendSibling(newValue.createCopy());
        }
        string.appendSibling(new TextComponentString(parts[parts.length - 1]));
        newSiblings.forEach(string::appendSibling);
        string.setStyle(src.getStyle());
        return string;
    }

}
