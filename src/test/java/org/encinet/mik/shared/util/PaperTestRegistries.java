package org.encinet.mik.shared.util;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

/** Scoped registry access with typed metadata providers, independent of Bukkit Registry initialization order. */
public final class PaperTestRegistries implements AutoCloseable {
    private final MockedStatic<RegistryAccess> access = mockStatic(RegistryAccess.class);

    public PaperTestRegistries() {
        try {
            Map<String, PotionEffectType> effects = new HashMap<>();
            Map<String, Attribute> attributes = new HashMap<>();
            RegistryAccess registries = mock(RegistryAccess.class, invocation -> {
                if (!invocation.getMethod().getName().equals("getRegistry"))
                    return RETURNS_DEFAULTS.answer(invocation);
                boolean potion = invocation.getArgument(0) == RegistryKey.MOB_EFFECT;
                boolean attribute = invocation.getArgument(0) == RegistryKey.ATTRIBUTE;
                return java.lang.reflect.Proxy.newProxyInstance(Registry.class.getClassLoader(), new Class<?>[]{Registry.class},
                        (_, method, args) -> {
                            if (method.getName().equals("get") || method.getName().equals("getOrThrow")) {
                                String name = Key.class.cast(args[0]).value();
                                if (potion) return effects.computeIfAbsent(name, PaperTestRegistries::effect);
                                if (attribute) return attributes.computeIfAbsent(name, PaperTestRegistries::attribute);
                                throw new UnsupportedOperationException("Only effect and attribute lookups are supported");
                            }
                            if (method.getName().equals("toString")) return "PaperTestRegistries";
                            throw new UnsupportedOperationException(method.getName());
                        });
            });
            access.when(RegistryAccess::registryAccess).thenReturn(registries);
        } catch (RuntimeException | Error failure) {
            try { access.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static Attribute attribute(String name) {
        return (Attribute) java.lang.reflect.Proxy.newProxyInstance(Attribute.class.getClassLoader(), new Class<?>[]{Attribute.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getKey", "key" -> NamespacedKey.minecraft(name);
                    case "translationKey" -> "attribute.name." + name;
                    case "toString" -> name;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static PotionEffectType effect(String name) {
        return mock(PotionEffectType.class, invocation -> switch (invocation.getMethod().getName()) {
            case "getKey" -> NamespacedKey.minecraft(name);
            case "translationKey" -> "effect.minecraft." + name;
            case "getEffectCategory" -> switch (name) {
                case "slowness", "poison", "weakness", "darkness" -> PotionEffectType.Category.HARMFUL;
                case "speed" -> PotionEffectType.Category.BENEFICIAL;
                default -> PotionEffectType.Category.NEUTRAL;
            };
            case "createEffect" -> new PotionEffect(PotionEffectType.class.cast(invocation.getMock()), invocation.getArgument(0), invocation.getArgument(1));
            default -> RETURNS_DEFAULTS.answer(invocation);
        });
    }

    @Override public void close() { access.close(); }
}
