package com.hengx.enigmaticenhance.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Inventory;

import com.hengx.enigmaticenhance.EnigmaticNetwork;
import com.hengx.enigmaticenhance.ValueEditorMenu;
import com.hengx.enigmaticenhance.ValueEditorPacket;
import com.hengx.enigmaticenhance.ValueEditorUtil;

/**
 * 1.4.2:数值编辑器界面(优化版)。
 *
 * <ul>
 *   <li>深色面板 + 边框 + 表头,行间交替底色;</li>
 *   <li>每行:属性名 / 当前值 (加成) / [-10][-1][+1][+10];</li>
 *   <li>底部:**AI 开/关**按钮(仅对非玩家生物可用)、翻页、页码、关闭。</li>
 * </ul>
 */
public class ValueEditorScreen extends AbstractContainerScreen<ValueEditorMenu> {

    private static final int ROW_H = 15;
    private static final int PER_PAGE = 8;
    private static final ResourceLocation AI_KEY = new ResourceLocation("enigmaticenhance", "no_ai");

    private final List<Attribute> attributes = new ArrayList<>();
    private int page;

    public ValueEditorScreen(ValueEditorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 340;
        this.imageHeight = 210;
    }

    private LivingEntity target() {
        Entity entity = this.minecraft != null && this.minecraft.level != null
            ? this.minecraft.level.getEntity(this.menu.targetId()) : null;
        return entity instanceof LivingEntity living ? living : null;
    }

    @Override
    protected void init() {
        super.init();
        LivingEntity target = target();
        this.attributes.clear();
        if (target != null) {
            this.attributes.addAll(ValueEditorUtil.attributesOf(target));
        }
        rebuild();
    }

    private void rebuild() {
        this.clearWidgets();
        int x = this.leftPos + 10;
        int y = this.topPos + 34;
        int start = this.page * PER_PAGE;
        int end = Math.min(start + PER_PAGE, this.attributes.size());
        for (int i = start; i < end; i++) {
            Attribute attribute = this.attributes.get(i);
            int rowY = y + (i - start) * ROW_H;
            int bx = x + 208;
            addRenderableWidget(Button.builder(Component.literal("-10"), b -> apply(attribute, -10.0D))
                .bounds(bx, rowY - 3, 30, 13).build());
            addRenderableWidget(Button.builder(Component.literal("-1"), b -> apply(attribute, -1.0D))
                .bounds(bx + 32, rowY - 3, 24, 13).build());
            addRenderableWidget(Button.builder(Component.literal("+1"), b -> apply(attribute, 1.0D))
                .bounds(bx + 58, rowY - 3, 24, 13).build());
            addRenderableWidget(Button.builder(Component.literal("+10"), b -> apply(attribute, 10.0D))
                .bounds(bx + 84, rowY - 3, 30, 13).build());
        }

        int totalPages = Math.max(1, (this.attributes.size() + PER_PAGE - 1) / PER_PAGE);
        int navY = this.topPos + this.imageHeight - 22;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (this.page > 0) { this.page--; rebuild(); }
        }).bounds(this.leftPos + 10, navY, 22, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            if (this.page + 1 < totalPages) { this.page++; rebuild(); }
        }).bounds(this.leftPos + 36, navY, 22, 16).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> this.onClose())
            .bounds(this.leftPos + this.imageWidth - 58, navY, 48, 16).build());

        // AI 开关(仅非玩家生物)
        LivingEntity target = target();
        if (target instanceof Mob mob) {
            addRenderableWidget(Button.builder(
                Component.literal("AI: " + (mob.isNoAi() ? "关" : "开")),
                b -> toggleAi())
                .bounds(this.leftPos + 74, navY, 70, 16).build());
        }
    }

    private void toggleAi() {
        LivingEntity target = target();
        if (!(target instanceof Mob mob)) {
            return;
        }
        mob.setNoAi(!mob.isNoAi());
        EnigmaticNetwork.CHANNEL.sendToServer(
            new ValueEditorPacket(mob.getId(), AI_KEY, 0.0D));
        rebuild();
    }

    private void apply(Attribute attribute, double delta) {
        LivingEntity target = target();
        if (target == null) {
            return;
        }
        double bonus = ValueEditorUtil.editorBonus(target, attribute) + delta;
        EnigmaticNetwork.CHANNEL.sendToServer(
            new ValueEditorPacket(target.getId(), ValueEditorUtil.idOf(attribute), bonus));
        ValueEditorUtil.setBonus(target, attribute, bonus);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        g.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xF0141414);      // 面板
        g.fill(x + 1, y + 1, x + this.imageWidth - 1, y + 30, 0xFF1F2A38);       // 标题区
        g.fill(x + 1, y + 30, x + this.imageWidth - 1, y + 31, 0xFF2E6EAA);      // 分隔线
        g.fill(x + 1, y + this.imageHeight - 26, x + this.imageWidth - 1,
            y + this.imageHeight - 25, 0xFF2E6EAA);
        g.renderOutline(x, y, this.imageWidth, this.imageHeight, 0xFF5C6B7A);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = this.leftPos + 10;
        int y = this.topPos + 34;
        LivingEntity target = target();
        if (target == null) {
            g.drawString(this.font, "目标已消失", x, y, 0xFF6060, false);
            return;
        }
        int start = this.page * PER_PAGE;
        int end = Math.min(start + PER_PAGE, this.attributes.size());
        for (int i = start; i < end; i++) {
            Attribute attribute = this.attributes.get(i);
            int rowY = y + (i - start) * ROW_H;
            if ((i - start) % 2 == 1) {
                g.fill(this.leftPos + 2, rowY - 4, this.leftPos + this.imageWidth - 2,
                    rowY + 10, 0x30FFFFFF);
            }
            String name = ValueEditorUtil.displayName(attribute).getString();
            double cur = ValueEditorUtil.currentValue(target, attribute);
            double bonus = ValueEditorUtil.editorBonus(target, attribute);
            g.drawString(this.font, name, x, rowY, 0xFFFFFF, false);
            String value = String.format("%.1f  (%+.1f)", cur, bonus);
            g.drawString(this.font, value, x + 140, rowY, bonus != 0.0D ? 0x7CFF9B : 0xBFBFBF, false);
        }
        int totalPages = Math.max(1, (this.attributes.size() + PER_PAGE - 1) / PER_PAGE);
        g.drawString(this.font, "第 " + (this.page + 1) + "/" + totalPages + " 页",
            this.leftPos + 64, this.topPos + this.imageHeight - 18, 0x9FB4C7, false);
        if (target instanceof Mob mob) {
            g.drawString(this.font, mob.isNoAi() ? "AI 已关闭" : "AI 正常",
                this.leftPos + 150, this.topPos + this.imageHeight - 18, 0xFFD479, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, 10, 8, 0xFFFFFF, false);
        LivingEntity target = target();
        if (target != null) {
            String info = target.getName().getString() + "  生命 " +
                String.format("%.1f/%.1f", target.getHealth(), target.getMaxHealth());
            g.drawString(this.font, info, 10, 19, 0x8FD3FF, false);
        }
    }
}