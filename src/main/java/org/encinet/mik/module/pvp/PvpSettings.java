package org.encinet.mik.module.pvp;

record PvpSettings(
        boolean enabled,
        boolean protectMobs,
        boolean allowMountedMobDamage,
        boolean enableOnDeath
) {
    PvpSettings withEnabled(boolean enabled) {
        return new PvpSettings(enabled, protectMobs, allowMountedMobDamage, enableOnDeath);
    }

    PvpSettings toggle(PvpSettingKey key) {
        return switch (key) {
            case ENABLED -> new PvpSettings(!enabled, protectMobs, allowMountedMobDamage, enableOnDeath);
            case PROTECT_MOBS -> new PvpSettings(enabled, !protectMobs, allowMountedMobDamage, enableOnDeath);
            case ALLOW_MOUNTED_DAMAGE -> new PvpSettings(enabled, protectMobs, !allowMountedMobDamage, enableOnDeath);
            case ENABLE_ON_DEATH -> new PvpSettings(enabled, protectMobs, allowMountedMobDamage, !enableOnDeath);
        };
    }
}
