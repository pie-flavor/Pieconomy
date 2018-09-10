package flavor.pie.pieconomy.client;

import net.minecraft.item.Item;

import java.util.Objects;

public class ItemState {
    private Item item;
    private int damage;

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public int getDamage() {
        return damage;
    }

    public void setDamage(int damage) {
        this.damage = damage;
    }

    public ItemState(Item item, int damage) {
        this.item = item;
        this.damage = damage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ItemState itemState = (ItemState) o;
        return damage == itemState.damage &&
                Objects.equals(item, itemState.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, damage);
    }
}
