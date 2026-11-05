package com.hengx.enigmaticenhance.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.hengx.enigmaticenhance.DebugLog;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;

/**
 * 2.4.0:假账生成器的"统计信息编辑器"。
 *
 * <p>列表列出所有自定义统计（跳跃、死亡、挖掘、击杀…），每行四个按钮：
 * {@code +1} {@code +10} {@code -1} {@code 清零}。点击后发 C2S 包由服务端校验并改写。</p>
 *
 * <p>数值显示用的是客户端自己那份 {@code ClientStatsCounter}（进服时服务端会整份同步一次），
 * 所以改完要等服务端回包刷新，这里只做"发送 + 本地预演"。</p>
 */
public class FakeLedgerScreen extends Screen {

    private static final int ROW_H = 20;
    private static final int ROWS = 9;

    private final List<Entry> all = new ArrayList<>();
    private int scroll;

    public FakeLedgerScreen() {
        super(Component.translatable("screen.enigmaticenhance.fake_ledger.title"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new FakeLedgerScreen());
    }

    @Override
    protected void init() {
        all.clear();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        for (ResourceLocation id : BuiltInRegistries.CUSTOM_STAT.keySet()) {
            all.add(new Entry(id));
        }
        all.sort(Comparator.comparing(e -> e.id.toString()));

        int top = 44;
        int listW = Math.min(360, this.width - 40);
        int left = (this.width - listW) / 2;

        // 翻页
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
            scroll = Math.max(0, scroll - ROWS);
            rebuildWidgets();
        }).bounds(left, top - 22, 24, 18).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
            scroll = Math.min(Math.max(0, all.size() - ROWS), scroll + ROWS);
            rebuildWidgets();
        }).bounds(left + 28, top - 22, 24, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
            .bounds(left + listW - 60, top - 22, 60, 18).build());

        for (int row = 0; row < ROWS; row++) {
            int index = scroll + row;
            if (index >= all.size()) {
                break;
            }
            Entry e = all.get(index);
            int y = top + row * ROW_H;
            addRenderableWidget(Button.builder(Component.literal("+1"), b -> e.send(0, 1))
                .bounds(left + listW - 148, y, 32, 18).build());
            addRenderableWidget(Button.builder(Component.literal("+10"), b -> e.send(0, 10))
                .bounds(left + listW - 114, y, 32, 18).build());
            addRenderableWidget(Button.builder(Component.literal("-1"), b -> e.send(0, -1))
                .bounds(left + listW - 80, y, 32, 18).build());
            addRenderableWidget(Button.builder(Component.literal("0"), b -> e.send(1, 0))
                .bounds(left + listW - 46, y, 32, 18).build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);

        int listW = Math.min(360, this.width - 40);
        int left = (this.width - listW) / 2;
        int top = 44;

        g.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        g.drawString(this.font, Component.literal("仅创造模式可改 · 只影响自己"), left, 30, 0x909090, false);

        for (int row = 0; row < ROWS; row++) {
            int index = scroll + row;
            if (index >= all.size()) {
                break;
            }
            Entry e = all.get(index);
            int y = top + row * ROW_H;
            g.drawString(this.font, e.label(), left + 4, y + 5, 0xE0E0E0, false);
            g.drawString(this.font, Component.literal(String.valueOf(e.value())),
                left + listW - 230, y + 5, 0xFFD479, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 一条统计。 */
    private final class Entry {

        private final ResourceLocation id;
        private final Stat<ResourceLocation> stat;

        private Entry(ResourceLocation id) {
            this.id = id;
            this.stat = resolveStat(id);
        }

        /** 4.3.1:对未注册的自定义 stat id,Stats.CUSTOM.get 会抛 NPE,这里吞掉。 */
        private static Stat<ResourceLocation> resolveStat(ResourceLocation id) {
            try {
                return id == null ? null : Stats.CUSTOM.get(id);
            }
            catch (RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        private Component label() {
            String path = id.getPath();
            if (path.startsWith("stat.")) {
                path = path.substring(5);
            }
            return Component.literal(path.replace('.', ' '));
        }

        private int value() {
            try {
                return minecraft != null && minecraft.player != null
                    ? minecraft.player.getStats().getValue(stat) : 0;
            }
            catch (RuntimeException | LinkageError e) {
                return 0;
            }
        }

        private void send(int mode, int amount) {
            try {
                com.hengx.enigmaticenhance.EnigmaticNetwork.CHANNEL.sendToServer(
                    new com.hengx.enigmaticenhance.EnigmaticNetwork.FakeLedgerPacket(
                        id.toString(), mode, amount));
                DebugLog.info("[FakeLedger] 发送改动 {} mode={} amount={}", id, mode, amount);
            }
            catch (RuntimeException | LinkageError e) {
                DebugLog.error("[FakeLedger] 发送失败", e);
            }
        }
    }
}
