package com.atom.chat.api.card;

import com.atom.chat.card.BuiltInCardAdapter;
import net.minecraft.text.HoverEvent;
import net.minecraft.item.ItemStack;

/** Client-side access to the actual item displayed by an item share card. */
public final class ItemCardItems {
    private ItemCardItems() {}

    /** Returns a separate copy, including components or NBT, or an empty stack. */
    public static ItemStack copyStack(ShareCard card) {
        if (card == null || card.template() != ShareCard.Template.ITEM) return ItemStack.EMPTY;
        HoverEvent hover = BuiltInCardAdapter.resolveItemHover(card);
        var item = hover == null ? null : hover.getValue(HoverEvent.Action.SHOW_ITEM);
        return item == null ? ItemStack.EMPTY : item.asStack().copy();
    }
}
