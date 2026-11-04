

package com.hengx.enigmaticenhance;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 本模组唯一的网络通道:把客户端"Ctrl+左键点了哪个槽位"告诉服务端,由服务端真正改 NBT 并同步回去。
 * (服务端拿不到 Ctrl 键状态,所以只能由客户端发起。)
 */
public final class EnigmaticNetwork {

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(EnigmaticEnhance.MODID, "main"),
        () -> VERSION, VERSION::equals, VERSION::equals);

    private EnigmaticNetwork() {}

    /** 在 mod 构造里调用(双端)。 */
    public static void register() {
        CHANNEL.registerMessage(0, BookTogglePacket.class,
            BookTogglePacket::encode, BookTogglePacket::decode, BookTogglePacket::handle);
        CHANNEL.registerMessage(1, ItemTogglePacket.class,
            ItemTogglePacket::encode, ItemTogglePacket::decode, ItemTogglePacket::handle);
        CHANNEL.registerMessage(2, BagHudPacket.class,
            BagHudPacket::encode, BagHudPacket::decode, BagHudPacket::handle);
        CHANNEL.registerMessage(3, NoClipPacket.class,
            NoClipPacket::encode, NoClipPacket::decode, NoClipPacket::handle);
        CHANNEL.registerMessage(4, TravelerDataPacket.class,
            TravelerDataPacket::encode, TravelerDataPacket::decode, TravelerDataPacket::handle);
        CHANNEL.registerMessage(5, CastSpellPacket.class,
            CastSpellPacket::encode, CastSpellPacket::decode, CastSpellPacket::handle);
        CHANNEL.registerMessage(6, ValueEditorPacket.class,
            ValueEditorPacket::encode, ValueEditorPacket::decode, ValueEditorPacket::handle);

          // 2.4.0:假账生成器 —— 客户端提交统计改动
          CHANNEL.registerMessage(7, FakeLedgerPacket.class,
              FakeLedgerPacket::encode, FakeLedgerPacket::decode, FakeLedgerPacket::handle);

          // 2.5.0:数值编辑器 —— 清除(移除)目标实体
          CHANNEL.registerMessage(8, ClearEntityPacket.class,
              ClearEntityPacket::encode, ClearEntityPacket::decode, ClearEntityPacket::handle);

          // 2.8.0:龙首穿墙开关(客户端按键 → 服务端)
          CHANNEL.registerMessage(9, NoClipTogglePacket.class,
              NoClipTogglePacket::encode, NoClipTogglePacket::decode, NoClipTogglePacket::handle);
    }

    /**
     * S2C(1.1.25c):旅行家帽子的"已探索群系数 / 已去过维度数"。
     * <p>
     * 计数只在服务端的玩家持久数据里,tooltip 在客户端渲染,所以计数变化(新群系/新维度)、登录、
     * 换维度、重生时各发一次;客户端存进 {@link TravelerHatSync} 供 tooltip 读。
     */
    public static final class TravelerDataPacket {

        private final int biomes;
        private final int dimensions;

        private TravelerDataPacket(int biomes, int dimensions) {
            this.biomes = biomes;
            this.dimensions = dimensions;
        }

        public static TravelerDataPacket of(int biomes, int dimensions) {
            return new TravelerDataPacket(biomes, dimensions);
        }

        public static void encode(TravelerDataPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.biomes);
            buf.writeVarInt(msg.dimensions);
        }

        public static TravelerDataPacket decode(FriendlyByteBuf buf) {
            return new TravelerDataPacket(buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(TravelerDataPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> TravelerHatSync.accept(msg.biomes, msg.dimensions));
            context.setPacketHandled(true);
        }
    }

    /**
     * S2C(1.1.25b):龙首护符的"飞行时穿过方块"开关。
     * <p>
     * 玩家的碰撞是客户端算的,所以必须把 {@code noPhysics} 的状态告诉客户端;
     * 服务端在授予/回收时各发一次,客户端只改本地玩家自己({@code client.NoClipClientHandler})。
     */
    public static final class NoClipPacket {

        private final boolean enabled;

        public NoClipPacket(boolean enabled) {
            this.enabled = enabled;
        }

        public static void encode(NoClipPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.enabled);
        }

        public static NoClipPacket decode(FriendlyByteBuf buf) {
            return new NoClipPacket(buf.readBoolean());
        }

        public static void handle(NoClipPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.hengx.enigmaticenhance.client.NoClipClientHandler.apply(msg.enabled)));
            context.setPacketHandled(true);
        }
    }

    /** C2S(2.8.0):切换"龙首是否穿墙"。无负载。 */
    public static final class NoClipTogglePacket {

        public static void encode(NoClipTogglePacket msg, FriendlyByteBuf buf) {
        }

        public static NoClipTogglePacket decode(FriendlyByteBuf buf) {
            return new NoClipTogglePacket();
        }

        public static void handle(NoClipTogglePacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) {
                    EarthAmuletHandler.toggleNoclip(player);
                }
            });
            context.setPacketHandled(true);
        }
    }

    /**
     * S2C:古旧书袋 HUD 数据(服务端算好每条加成,客户端只显示)。
     * 只在内容签名变化时发一次({@link BagHudSync}),不是每 tick 发。
     * <p>
     * 1.1.19:1.1.18 及之前已经占用的 <b>message id 2</b> 直接复用(不新开通道/不新开 id),
     * 只是在每条 entry 后面追加了一个 {@code detail} 字符串(石英花属性行 = {@code amount|operation|remaining},
     * 其它条目为空串)。两端同版本,不需要兼容旧格式。
     */
    public static final class BagHudPacket {

        private final java.util.List<BagHudEffects.Entry> entries;

        public BagHudPacket(java.util.List<BagHudEffects.Entry> entries) {
            this.entries = java.util.List.copyOf(entries);
        }

        public static void encode(BagHudPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.entries.size());
            for (BagHudEffects.Entry e : msg.entries) {
                buf.writeUtf(e.bookId());
                buf.writeUtf(e.effectKey());
                buf.writeUtf(e.value());
                buf.writeBoolean(e.active());
                buf.writeUtf(e.detail());
            }
        }

        public static BagHudPacket decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            java.util.List<BagHudEffects.Entry> list = new java.util.ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(new BagHudEffects.Entry(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readBoolean(),
                    buf.readUtf()));
            }
            return new BagHudPacket(list);
        }

        public static void handle(BagHudPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> BagHudSync.accept(msg.entries));
            context.setPacketHandled(true);
        }
    }

    /**
     * C2S:请求切换某个容器槽位里"支持 Ctrl+左键 开关的物品"(万钧之护卷轴的雷击 / 血战沙场之证的锁血)。
     * 和书籍开关共用这一条通道(同一个 SimpleChannel,只是又一个 message id),不另起网络通道。
     * 1.1.15 起它不再写死给卷轴,统一交给 {@link ItemToggleHandler}。
     */
    public static final class ItemTogglePacket {

        private final int containerId;
        private final int slotIndex;

        public ItemTogglePacket(int containerId, int slotIndex) {
            this.containerId = containerId;
            this.slotIndex = slotIndex;
        }

        public static void encode(ItemTogglePacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.containerId);
            buf.writeVarInt(msg.slotIndex);
        }

        public static ItemTogglePacket decode(FriendlyByteBuf buf) {
            return new ItemTogglePacket(buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(ItemTogglePacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer sender = context.getSender();
                if (sender == null) {
                    return;
                }
                // 服务端重新校验(菜单 id、槽位范围、槽里确实是支持开关的物品、配置允许)后才改 NBT。
                ItemToggleHandler.handleToggleRequest(sender, msg.containerId, msg.slotIndex);
            });
            context.setPacketHandled(true);
        }
    }

    /** C2S:请求切换某个容器槽位里目标书籍的禁用状态。 */
    public static final class BookTogglePacket {

        private final int containerId;
        private final int slotIndex;

        public BookTogglePacket(int containerId, int slotIndex) {
            this.containerId = containerId;
            this.slotIndex = slotIndex;
        }

        public static void encode(BookTogglePacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.containerId);
            buf.writeVarInt(msg.slotIndex);
        }

        public static BookTogglePacket decode(FriendlyByteBuf buf) {
            return new BookTogglePacket(buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(BookTogglePacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer sender = context.getSender();
                if (sender == null) {
                    return;
                }
                // 服务端自己重新校验:必须是玩家当前打开的那个菜单、槽位合法、且确实是目标书籍。
                AbstractContainerMenu menu = sender.containerMenu;
                if (menu == null || menu.containerId != msg.containerId) {
                    return;
                }
                if (msg.slotIndex < 0 || msg.slotIndex >= menu.slots.size()) {
                    return;
                }
                Slot slot = menu.getSlot(msg.slotIndex);
                ItemStack stack = slot.getItem();
                if (!BooksHandler.isTargetBook(stack)) {
                    return;
                }
                boolean now = BooksHandler.toggle(stack);
                slot.setChanged();
                menu.broadcastChanges();
                if (RingConfig.debugLog()) {
                    DebugLog.info("[书籍开关] 玩家 {} 槽 {} -> {}({})", sender.getGameProfile().getName(),
                        msg.slotIndex, now ? "已禁用" : "已启用",
                        net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
                }
            });
            context.setPacketHandled(true);
        }
    }
    /** 1.2.24:C2S —— 客户端"空挥/对空开火"时请求服务端施法。 */
    public static class CastSpellPacket {

        public CastSpellPacket() {
        }

        public static void encode(CastSpellPacket msg, FriendlyByteBuf buf) {
        }

        public static CastSpellPacket decode(FriendlyByteBuf buf) {
            return new CastSpellPacket();
        }

        public static void handle(CastSpellPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                try {
                    com.hengx.enigmaticenhance.ElementalSpellCastHandler.castFromClientSwing(context.getSender());
                }
                catch (Throwable ignored) {
                    // 纯附加效果
                }
            });
            context.setPacketHandled(true);
        }
    }



      /**
       * C2S(2.4.0):假账生成器改一条统计。
       * <p>服务端会再校验一次 {@code isCreative()}，非创造模式直接丢弃。</p>
       */
      public static final class FakeLedgerPacket {

          private final String statId;
          private final int mode;
          private final int amount;

          public FakeLedgerPacket(String statId, int mode, int amount) {
              this.statId = statId;
              this.mode = mode;
              this.amount = amount;
          }

          public static void encode(FakeLedgerPacket msg, FriendlyByteBuf buf) {
              buf.writeUtf(msg.statId == null ? "" : msg.statId, 128);
              buf.writeVarInt(msg.mode);
              buf.writeVarInt(msg.amount);
          }

          public static FakeLedgerPacket decode(FriendlyByteBuf buf) {
              return new FakeLedgerPacket(buf.readUtf(128), buf.readVarInt(), buf.readVarInt());
          }

          public static void handle(FakeLedgerPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
              NetworkEvent.Context context = contextSupplier.get();
              context.enqueueWork(() -> {
                  try {
                      net.minecraft.server.level.ServerPlayer sender = context.getSender();
                      if (sender != null) {
                          FakeLedgerHandler.apply(sender, msg.statId, msg.mode, msg.amount);
                      }
                  }
                  catch (RuntimeException | LinkageError e) {
                      DebugLog.error("[FakeLedger] 处理改动包失败", e);
                  }
              });
              context.setPacketHandled(true);
          }
      }


      /** C2S(2.5.0):移除一个实体。服务端校验:创造或 OP、距离 8 格内。 */
      public static final class ClearEntityPacket {

          private final int entityId;

          public ClearEntityPacket(int entityId) {
              this.entityId = entityId;
          }

          public static void encode(ClearEntityPacket msg, FriendlyByteBuf buf) {
              buf.writeVarInt(msg.entityId);
          }

          public static ClearEntityPacket decode(FriendlyByteBuf buf) {
              return new ClearEntityPacket(buf.readVarInt());
          }

          public static void handle(ClearEntityPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
              NetworkEvent.Context context = contextSupplier.get();
              context.enqueueWork(() -> {
                  try {
                      net.minecraft.server.level.ServerPlayer sender = context.getSender();
                      if (sender == null) {
                          return;
                      }
                      if (!sender.isCreative() && !sender.hasPermissions(2)) {
                          return;
                      }
                      net.minecraft.world.entity.Entity target = sender.level().getEntity(msg.entityId);
                      if (target == null || target == sender) {
                          return;
                      }
                      if (target.distanceToSqr(sender) > 64.0D) {
                          return;
                      }
                      DebugLog.info("[ValueEditor] {} 清除实体 {}", sender.getName().getString(), target);
                      target.discard();
                  }
                  catch (RuntimeException | LinkageError e) {
                      DebugLog.error("[ValueEditor] 清除实体失败", e);
                  }
              });
              context.setPacketHandled(true);
          }
      }
}
