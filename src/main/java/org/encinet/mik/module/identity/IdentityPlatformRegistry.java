package org.encinet.mik.module.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

final class IdentityPlatformRegistry {

    private final NavigableMap<String, Entry> entries = new TreeMap<>();

    synchronized IdentityPlatformRegistration register(IdentityPlatform platform) {
        Objects.requireNonNull(platform, "platform");
        Entry entry = entries.get(platform.id());
        if (entry == null) {
            entry = new Entry(platform);
            entries.put(platform.id(), entry);
        } else if (!entry.platform.equals(platform)) {
            throw new IllegalArgumentException(
                    "Identity platform '" + platform.id() + "' is already registered");
        }
        entry.leases++;
        return new Registration(entry);
    }

    synchronized Optional<IdentityPlatform> find(String platformId) {
        Entry entry = entries.get(ExternalIdentityKey.normalizePlatform(platformId));
        return entry == null ? Optional.empty() : Optional.of(entry.platform);
    }

    synchronized List<IdentityPlatform> platforms() {
        List<IdentityPlatform> platforms = new ArrayList<>(entries.size());
        entries.values().forEach(entry -> platforms.add(entry.platform));
        return List.copyOf(platforms);
    }

    synchronized List<String> suggestions(String input) {
        String prefix = Objects.requireNonNull(input, "input").toLowerCase(Locale.ROOT);
        return entries.navigableKeySet().stream()
                .filter(id -> id.startsWith(prefix))
                .toList();
    }

    synchronized int size() {
        return entries.size();
    }

    synchronized void clear() {
        entries.clear();
    }

    private static final class Entry {
        private final IdentityPlatform platform;
        private int leases;

        private Entry(IdentityPlatform platform) {
            this.platform = platform;
        }
    }

    private final class Registration implements IdentityPlatformRegistration {
        private final Entry entry;
        private boolean open = true;

        private Registration(Entry entry) {
            this.entry = entry;
        }

        @Override
        public IdentityPlatform platform() {
            return entry.platform;
        }

        @Override
        public boolean isActive() {
            synchronized (IdentityPlatformRegistry.this) {
                return open && entries.get(entry.platform.id()) == entry;
            }
        }

        @Override
        public void close() {
            synchronized (IdentityPlatformRegistry.this) {
                if (!open) {
                    return;
                }
                open = false;
                if (entries.get(entry.platform.id()) != entry) {
                    return;
                }
                entry.leases--;
                if (entry.leases == 0) {
                    entries.remove(entry.platform.id());
                }
            }
        }
    }
}
