package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.1.28c 自检:【纯净符文】(-D 开关,由 {@link EarthAmuletSelfTest} 串起来跑)。
 * <ul>
 * <li>T28a 定义:id / 堆叠 16 / 在创造标签页 / 配方(4 石 + 灵液滴 ⇒ 4 个)/ tooltip 3 行 /
 *     中英 lang / 模型 + 贴图 + 配方 json / 两个实体类型都已注册 / {@code ModIds.problems()=[]};</li>
 * <li>T28b 范围伤害:落点 3 格内、看得见的僵尸掉血;墙后那只**一点血都不掉**;</li>
 * <li>T28c 数值:亡灵 15 点、非亡灵(牛)5 点(用 hurt 前后血量差断言);</li>
 * <li>T28d 来源:{@code getEntity()} 是投掷者、{@code is(DamageTypes.MAGIC)} 为 true;</li>
 * <li>T28e 视觉:结算会在落点 spawn 一个 {@link PureRuneFlash},且 1 秒后自己消失。</li>
 * </ul>
 * 直接调 {@link PureRuneDamage#detonate} 而不是真扔一个投掷物 —— 投掷物的飞行/命中是原版
 * {@code ThrowableItemProjectile} 的既有逻辑,这里要验证的是我们自己的"落点结算"。
 */
final class PureRuneSelfTest {

    private static final String TAG = "[PureRuneSelfTest]";
    private static final int Y = 110;
    /** 测试用落点:一个 3x3 的空地中心。 */
    private static final double CX = 8.5D;
    private static final double CZ = 8.5D;

    private PureRuneSelfTest() {}

    static boolean run(ServerLevel level, Player player) {
        boolean a = testDefinition(level, player);
        boolean b = testRangeAndWall(level, player);
        boolean c = testDamageAmounts(level, player);
        boolean d = testSource(level, player);
        boolean e = testFlash(level, player);
        boolean all = a && b && c && d && e;
        log(" 纯净符文小结 T28a=" + a + " T28b(范围/墙)=" + b + " T28c(5/15)=" + c
            + " T28d(来源)=" + d + " T28e(白光)=" + e + " => " + (all ? "PASS" : "FAIL"));
        return all;
    }

    // ------------------------------------------------------------------ T28a 定义

    private static boolean testDefinition(ServerLevel level, Player player) {
        log(" --- T28a 纯净符文定义 / 配方 / tooltip / 资源 / 实体注册");
        boolean ok = true;
        Item item = ModItems.pureRuneOrNull();
        ResourceLocation id = item == null ? null : ForgeRegistries.ITEMS.getKey(item);
        log(" T28a 物品 id=" + id + " [应为 enigmaticenhance:pure_rune]");
        ok &= item != null && "enigmaticenhance:pure_rune".equals(String.valueOf(id));
        if (item == null) {
            check("T28a", false);
            return false;
        }
        ItemStack stack = new ItemStack(item);
        log(" T28a 最大堆叠=" + stack.getMaxStackSize() + " [应 16];可投掷(Item.use)="
            + (item instanceof PureRuneItem) + ";在创造标签页=" + CreativeTabContents.items().contains(item));
        ok &= stack.getMaxStackSize() == 16 && item instanceof PureRuneItem
            && CreativeTabContents.items().contains(item);

        Item ichor = ForgeRegistries.ITEMS.getValue(new ResourceLocation("enigmaticaddons", "ichor_droplet"));
        log(" T28a 灵液滴 enigmaticaddons:ichor_droplet 存在=" + (ichor != null) + " [应 true]");
        ok &= ichor != null;
        ResourceLocation recipeId = new ResourceLocation(EnigmaticEnhance.MODID, ModIds.PURE_RUNE_PATH);
        log(" T28a 配方 " + recipeId + " 存在=" + level.getRecipeManager().byKey(recipeId).isPresent());
        ok &= level.getRecipeManager().byKey(recipeId).isPresent();
        if (ichor != null) {
            // 有序配方 " S " / "SIS" / " S ":槽位 1、3、4、5、7
            TransientCraftingContainer grid =
                new TransientCraftingContainer(new CraftingMenu(0, player.getInventory()), 3, 3);
            grid.setItem(1, new ItemStack(Items.STONE));
            grid.setItem(3, new ItemStack(Items.STONE));
            grid.setItem(4, new ItemStack(ichor));
            grid.setItem(5, new ItemStack(Items.STONE));
            grid.setItem(7, new ItemStack(Items.STONE));
            var found = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
            Crafted crafted = found.isEmpty() ? null
                : new Crafted(found.get(), found.get().assemble(grid, level.registryAccess()));
            boolean shape = crafted != null && crafted.output().getItem() == item
                && crafted.output().getCount() == 4;
            log(" T28a 4 石头 + 灵液滴(" + (crafted == null ? "无配方" : crafted.recipe().getId()) + ")-> "
                + (crafted == null ? "无"
                    : (ForgeRegistries.ITEMS.getKey(crafted.output().getItem()) + " x"
                        + crafted.output().getCount())) + " [应 enigmaticenhance:pure_rune x4]");
            ok &= shape;
        }

        List<net.minecraft.network.chat.Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, null, tooltip, TooltipFlag.Default.NORMAL);
        List<String> lines = new ArrayList<>();
        for (int i = 1; i < tooltip.size(); i++) {
            lines.add(tooltip.get(i).getString());
        }
        boolean clean = lines.stream().noneMatch(line -> line.trim().isEmpty() || line.contains("%s")
            || line.contains("%d"));
        log(" T28a tooltip 效果行数=" + lines.size() + " [应 3:1.1.28h 起写全数值];无占位符/空行=" + clean);
        for (String line : lines) {
            log(" T28a   " + line);
        }
        ok &= lines.size() == 3 && clean;

        for (String lang : new String[] { "zh_cn", "en_us" }) {
            var root = readLang(lang);
            String name = root == null ? null : text(root, "item.enigmaticenhance.pure_rune");
            int keys = 0;
            if (root != null) {
                for (String key : new String[] { "header", "effect", "ability", "blocked" }) {
                    if (text(root, "tooltip.enigmaticenhance.pure_rune." + key) != null) {
                        ++keys;
                    }
                }
            }
            log(" T28a " + lang + " 物品名=" + name + ";tooltip 键=" + keys + "/4 [应 4]");
            ok &= name != null && !name.isEmpty() && keys == 4;
        }

        // 1.1.28d:元素模型(六棱柱 + 倒角 + 浮雕符文层),三张 16x16 贴图(顶/侧/符文)
        String modelJson = resourceText("/assets/enigmaticenhance/models/item/pure_rune.json");
        boolean model = modelJson != null && modelJson.contains("\"elements\"")
            && modelJson.contains("pure_rune_top")
            && modelJson.contains("pure_rune_side") && modelJson.contains("pure_rune_rune")
            && modelJson.contains("\"display\"");
        boolean recipe = resource("/data/enigmaticenhance/recipes/pure_rune.json") != null;
        int maps = 0;
        StringBuilder mapInfo = new StringBuilder();
        for (String map : new String[] { "pure_rune", "pure_rune_top", "pure_rune_side", "pure_rune_rune" }) {
            byte[] png = resourceBytes("/assets/enigmaticenhance/textures/item/" + map + ".png");
            boolean texture = png != null && png.length > 24 && (png[0] & 0xFF) == 0x89 && png[1] == 'P';
            int width = texture ? ((png[16] & 0xFF) << 24) | ((png[17] & 0xFF) << 16)
                | ((png[18] & 0xFF) << 8) | (png[19] & 0xFF) : -1;
            int height = texture ? ((png[20] & 0xFF) << 24) | ((png[21] & 0xFF) << 16)
                | ((png[22] & 0xFF) << 8) | (png[23] & 0xFF) : -1;
            if (texture && width > 0 && width == height) {
                ++maps;
            }
            mapInfo.append(map).append('=').append(width).append('x').append(height).append(' ');
        }
        log(" T28a 模型(元素/三贴图/display)=" + model + " 配方 json=" + recipe + " 贴图 " + mapInfo
            + "[四张都应存在且为正方形]");
        ok &= model && recipe && maps == 4;

        EntityType<?> projectile = ForgeRegistries.ENTITY_TYPES.getValue(
            new ResourceLocation(EnigmaticEnhance.MODID, ModIds.PURE_RUNE_PATH));
        EntityType<?> flash = ForgeRegistries.ENTITY_TYPES.getValue(
            new ResourceLocation(EnigmaticEnhance.MODID, ModIds.PURE_RUNE_FLASH_PATH));
        log(" T28a 实体 enigmaticenhance:pure_rune=" + (projectile != null)
            + " enigmaticenhance:pure_rune_flash=" + (flash != null) + " [都应已注册]");
        ok &= projectile != null && flash != null;

        List<String> problems = ModIds.problems();
        log(" T28a ModIds.problems()=" + problems + " [必须空]");
        ok &= problems.isEmpty();
        check("T28a", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T28b 范围 + 墙

    private static boolean testRangeAndWall(ServerLevel level, Player player) {
        log(" --- T28b 落点半径 3 格:看得见的掉血,墙后的不掉");
        boolean ok = true;
        prepareArea(level);
        Vec3 center = new Vec3(CX, Y, CZ);

        Zombie open = spawnZombie(level, CX + 2.0D, CZ);
        Zombie behind = spawnZombie(level, CX, CZ + 2.5D);
        if (open == null || behind == null) {
            log(" T28b 无法生成测试僵尸 [FAIL]");
            check("T28b", false);
            return false;
        }
        // 在落点与墙后僵尸之间砌一整面墙(y=110..112,z=10)
        int wallZ = 10;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                level.setBlockAndUpdate(new BlockPos(8 + dx, Y + dy, wallZ),
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            }
        }
        float openBefore = open.getHealth();
        float behindBefore = behind.getHealth();
        boolean losOpen = !PureRuneDamage.blocked(level, center, open);
        boolean losBehind = PureRuneDamage.blocked(level, center, behind);
        float openAfter = open.getHealth();
        float behindAfter = behind.getHealth();
        log(" T28b 无遮挡僵尸:血量 " + openBefore + " -> " + openAfter + " [应掉血];视线被挡=" + losBehind
            + "(墙后)、" + losOpen + "(无遮挡)[应 true / false]");
        log(" T28b 墙后僵尸:血量 " + behindBefore + " -> " + behindAfter + " [应一点不掉]");
        ok &= losOpen && losBehind && openAfter < openBefore && behindAfter == behindBefore;

        // 超出半径的一只:3.5 格外,不该掉血
        Zombie far = spawnZombie(level, CX + 3.5D, CZ);
        if (far != null) {
            // 生成点可能高于地面,落地的摔落伤害会污染"掉血"统计:先把所有目标恢复满血
            for (var target : new net.minecraft.world.entity.LivingEntity[] { open, behind, far }) {
                target.setHealth(target.getMaxHealth());
                target.setAbsorptionAmount(0.0F);
                target.invulnerableTime = 0;
                target.removeAllEffects();
            }
            float farBefore = far.getHealth();
            PureRuneDamage.detonate(level, center, null, player);
            log(" T28b 3.5 格外的僵尸:血量 " + farBefore + " -> " + far.getHealth() + " [应不受伤]");
            ok &= far.getHealth() == farBefore;
            far.discard();
        }
        open.discard();
        behind.discard();
        clearWall(level, wallZ);
        check("T28b", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T28c 5 / 15

    private static boolean testDamageAmounts(ServerLevel level, Player player) {
        log(" --- T28c 数值:亡灵 15 点 / 非亡灵 5 点");
        boolean ok = true;
        prepareArea(level);
        Vec3 center = new Vec3(CX, Y, CZ);
        Zombie undead = spawnZombie(level, CX + 1.0D, CZ);
        Cow cow = spawnCow(level, CX - 1.0D, CZ);
        if (undead == null || cow == null) {
            log(" T28c 无法生成测试目标 [FAIL]");
            check("T28c", false);
            return false;
        }
        for (var target : new net.minecraft.world.entity.LivingEntity[] { undead, cow }) {
            target.setHealth(target.getMaxHealth());
            target.setAbsorptionAmount(0.0F);
            target.invulnerableTime = 0;
            target.removeAllEffects();
        }
        float undeadMax = undead.getMaxHealth();
        float cowMax = cow.getMaxHealth();
        log(" T28c 目标血量:僵尸 " + undeadMax + "(亡灵=" + (undead.getMobType() == net.minecraft.world.entity.MobType.UNDEAD)
            + ")、牛 " + cowMax);
        log(" T28c 前:僵尸 hp=" + undead.getHealth() + " 吸收=" + undead.getAbsorptionAmount()
            + " 护甲=" + undead.getArmorValue() + ";牛 hp=" + cow.getHealth()
            + " 吸收=" + cow.getAbsorptionAmount() + " 护甲=" + cow.getArmorValue());
        ok &= undead.getMobType() == net.minecraft.world.entity.MobType.UNDEAD;
        PureRuneDamage.detonate(level, center, null, player);
        float undeadLost = undeadMax - undead.getHealth();
        float cowLost = cowMax - cow.getHealth();
        log(" T28c 后:僵尸 hp=" + undead.getHealth() + " 掉血 " + undeadLost + " [应 15];牛 hp="
            + cow.getHealth() + " 掉血 " + cowLost + " [应 5]");
        boolean undeadOk = Math.abs(undeadLost - 15.0F) < 0.01F;
        boolean cowOk = Math.abs(cowLost - 5.0F) < 0.01F;
        ok &= undeadOk && cowOk;
        undead.discard();
        cow.discard();
        check("T28c", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T28d 伤害来源

    private static boolean testSource(ServerLevel level, Player player) {
        log(" --- T28d 伤害来源:玩家 + 魔法");
        boolean ok = true;
        DamageSource source = PureRuneDamage.source(level, null, player);
        Entity attacker = source.getEntity();
        boolean magic = source.is(DamageTypes.MAGIC);
        log(" T28d getEntity()=" + (attacker == null ? "null"
            : attacker.getName().getString() + "(=投掷者:" + (attacker == player) + ")")
            + ";is(DamageTypes.MAGIC)=" + magic + " [应为 true];typeHolder=" + source.typeHolder().unwrapKey()
            .map(key -> key.location().toString()).orElse("-"));
        ok &= attacker == player && magic;
        check("T28d", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T28e 白光视觉体

    private static boolean testFlash(ServerLevel level, Player player) {
        log(" --- T28e 落点白光视觉体");
        boolean ok = true;
        prepareArea(level);
        Vec3 center = new Vec3(CX, Y, CZ);
        PureRuneDamage.detonate(level, center, null, player);
        AABB box = new AABB(center, center).inflate(6.0D);
        List<PureRuneFlash> flashes = level.getEntitiesOfClass(PureRuneFlash.class, box);
        log(" T28e 落点生成的视觉体数量=" + flashes.size() + " [应 >=1];存在时长="
            + (flashes.isEmpty() ? "-" : (PureRuneFlash.LIFETIME + " tick(1 秒)")));
        ok &= !flashes.isEmpty() && PureRuneFlash.LIFETIME == 20;
        for (PureRuneFlash flash : flashes) {
            ok &= !flash.isPickable() && flash.noPhysics;
            flash.discard();
        }
        check("T28e", ok);
        return ok;
    }

    // ------------------------------------------------------------------ 工具

    private record Crafted(CraftingRecipe recipe, ItemStack output) {}

    private static Crafted craft(ServerLevel level, Player player, ItemStack... items) {
        TransientCraftingContainer grid =
            new TransientCraftingContainer(new CraftingMenu(0, player.getInventory()), 3, 3);
        for (int i = 0; i < items.length && i < grid.getContainerSize(); i++) {
            grid.setItem(i, items[i].copy());
        }
        var found = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
        if (found.isEmpty()) {
            return null;
        }
        CraftingRecipe recipe = found.get();
        return new Crafted(recipe, recipe.assemble(grid, level.registryAccess()));
    }

    /** 一块 9x9 的石头地板 + 上方 4 层空气(每次测试前重置,保证射线判定可预期)。 */
    private static void prepareArea(ServerLevel level) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 6; dz++) {
                level.setBlockAndUpdate(new BlockPos(8 + dx, Y - 1, 8 + dz),
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy <= 4; dy++) {
                    level.setBlockAndUpdate(new BlockPos(8 + dx, Y + dy, 8 + dz),
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void clearWall(ServerLevel level, int wallZ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                level.setBlockAndUpdate(new BlockPos(8 + dx, Y + dy, wallZ),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static Zombie spawnZombie(ServerLevel level, double x, double z) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            return null;
        }
        zombie.moveTo(x, Y, z, 0.0F, 0.0F);
        zombie.setPersistenceRequired();
        noAi(zombie);
        level.addFreshEntity(zombie);
        zombie.setHealth(zombie.getMaxHealth());
        return zombie;
    }

    private static Cow spawnCow(ServerLevel level, double x, double z) {
        Cow cow = EntityType.COW.create(level);
        if (cow == null) {
            return null;
        }
        cow.moveTo(x, Y, z, 0.0F, 0.0F);
        cow.setPersistenceRequired();
        noAi(cow);
        level.addFreshEntity(cow);
        cow.setHealth(cow.getMaxHealth());
        return cow;
    }

    private static void noAi(Mob mob) {
        try {
            mob.setNoAi(true);
        }
        catch (Throwable ignored) {
            // 不是所有版本都有这个 setter;没有也不影响测试(只差它们会乱走)
        }
    }

    private static java.io.InputStream resource(String path) {
        try {
            return PureRuneSelfTest.class.getResourceAsStream(path);
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static byte[] resourceBytes(String path) {
        try (java.io.InputStream in = resource(path)) {
            return in == null ? null : in.readAllBytes();
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static String resourceText(String path) {
        byte[] bytes = resourceBytes(path);
        return bytes == null ? null : new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static com.google.gson.JsonObject readLang(String lang) {
        try (java.io.InputStream in = resource("/assets/enigmaticenhance/lang/" + lang + ".json")) {
            if (in == null) {
                return null;
            }
            try (var reader = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
                return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
        catch (Throwable t) {
            log(" T28a 解析 " + lang + " 失败: " + t);
            return null;
        }
    }

    private static String text(com.google.gson.JsonObject root, String key) {
        return root != null && root.has(key) ? root.get(key).getAsString() : null;
    }

    private static void check(String test, boolean ok) {
        log(" " + test + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    private static void log(String message) {
        DebugLog.info(TAG + message);
    }
}
