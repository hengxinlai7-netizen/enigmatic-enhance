package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** 1.3.1:数值编辑器的空容器(只用来承载 GUI,不存物品)。 */
public class ValueEditorMenu extends AbstractContainerMenu {

    private final int targetId;

    public ValueEditorMenu(int id, int targetId) {
        super(ModMenus.VALUE_EDITOR.get(), id);
        this.targetId = targetId;
    }

    public int targetId() {
        return this.targetId;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}