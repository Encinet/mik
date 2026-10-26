package org.encinet.mik.test;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Color;
import org.bukkit.FeatureFlag;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Minimal API registries for potion behavior tests without a running Minecraft server. */
public final class PotionTestRegistryAccess implements RegistryAccess {
    private final Map<String, PotionEffectType> effects = new HashMap<>();

    @Override
    public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
        return registry(type == PotionEffectType.class);
    }

    @Override
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> key) {
        return registry(key == RegistryKey.MOB_EFFECT);
    }

    @SuppressWarnings("unchecked")
    private <T extends Keyed> Registry<T> registry(boolean potion) {
        return (Registry<T>) Proxy.newProxyInstance(Registry.class.getClassLoader(), new Class<?>[]{Registry.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("get") || method.getName().equals("getOrThrow")) {
                        if (!potion) throw new UnsupportedOperationException("Only potion lookups are supported");
                        String name = ((Key) args[0]).value();
                        return effects.computeIfAbsent(name, Type::new);
                    }
                    if (method.getName().equals("toString")) return "PotionTestRegistry";
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class Type extends PotionEffectType {
        private final String name;
        Type(String name) { this.name = name; }
        @Override public NamespacedKey getKey() { return NamespacedKey.minecraft(name); }
        @Override public String getName() { return name; }
        @Override public String getTranslationKey() { return translationKey(); }
        @Override public String translationKey() { return "effect.minecraft." + name; }
        @Override public Category getEffectCategory() {
            return switch (name) {
                case "slowness", "poison", "weakness", "darkness" -> Category.HARMFUL;
                case "speed" -> Category.BENEFICIAL;
                default -> Category.NEUTRAL;
            };
        }
        @Override public PotionEffectTypeCategory getCategory() {
            return PotionEffectTypeCategory.valueOf(getEffectCategory().name());
        }
        @Override public PotionEffect createEffect(int duration, int amplifier) {
            return new PotionEffect(this, duration, amplifier);
        }
        @Override public boolean isInstant() { return false; }
        @Override public Color getColor() { return Color.RED; }
        @Override public double getDurationModifier() { return 1; }
        @Override public int getId() { return 0; }
        @Override public Map<Attribute, AttributeModifier> getEffectAttributes() { return Map.of(); }
        @Override public double getAttributeModifierAmount(Attribute attribute, int amplifier) { return 0; }
        @Override public Set<FeatureFlag> requiredFeatures() { return Set.of(); }
    }
}
