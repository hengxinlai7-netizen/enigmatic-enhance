package com.hengx.enigmaticenhance;

/** 1.6.2:识别"降灵"(spirit_mediumship_spell)等模组的召唤物,避免给它们上我们的 Boss 加成。 */
public final class SummonFilter {

    private SummonFilter() {
    }

    public static boolean isSummon(Object entity) {
        try {
            if (entity == null) {
                return false;
            }
            String name = entity.getClass().getName();
            return name.startsWith("com.inolia_zaicek.spirit_mediumship_spell.entity.")
                || name.contains("Summoned");
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}