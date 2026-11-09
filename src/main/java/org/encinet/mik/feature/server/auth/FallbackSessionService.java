package org.encinet.mik.feature.server.auth;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.InsecurePublicKeyException;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import com.mojang.authlib.minecraft.SessionService;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.services.ProfileResult;

import java.net.InetAddress;
import java.util.UUID;
import java.util.function.Consumer;

/** Runs on Paper's authentication threads; never reads Bukkit state. */
final class FallbackSessionService implements SessionService, AutoCloseable {
    private final SessionService upstream;
    private final AuthenticationFallbackStore cache;
    private final boolean upstreamChecksAddress;
    private final Consumer<Exception> storageFailure;
    private final Consumer<UUID> fallbackUsed;
    private volatile boolean active = true;

    FallbackSessionService(SessionService upstream, AuthenticationFallbackStore cache, boolean upstreamChecksAddress,
                           Consumer<Exception> storageFailure, Consumer<UUID> fallbackUsed) {
        this.upstream = upstream;
        this.cache = cache;
        this.upstreamChecksAddress = upstreamChecksAddress;
        this.storageFailure = storageFailure;
        this.fallbackUsed = fallbackUsed;
    }

    @Override public ProfileResult hasJoinedServer(String name, String serverId, InetAddress address)
            throws AuthenticationUnavailableException {
        ProfileResult result;
        try {
            // The hook requests addresses for caching without changing the original upstream IP policy.
            result = upstream.hasJoinedServer(name, serverId, upstreamChecksAddress ? address : null);
        } catch (AuthenticationUnavailableException unavailable) {
            if (active) {
                try {
                    ProfileResult fallback = cache.find(name, address);
                    if (fallback != null && active) {
                        fallbackUsed.accept(fallback.profile().id());
                        return fallback;
                    }
                } catch (Exception failure) { failStorage(failure); }
            }
            throw unavailable;
        }
        // A null result is an explicit rejection, never a reason to use a cached identity.
        if (result != null && active) {
            try { cache.remember(name, address, result); }
            catch (Exception failure) { failStorage(failure); }
        }
        return result;
    }

    private synchronized void failStorage(Exception failure) {
        if (!active) return;
        active = false;
        storageFailure.accept(failure);
    }

    @Override public void close() { active = false; }

    @Override public void joinServer(UUID id, String token, String serverId) throws AuthenticationException {
        upstream.joinServer(id, token, serverId);
    }
    @Override public Property getPackedTextures(GameProfile profile) { return upstream.getPackedTextures(profile); }
    @Override public MinecraftProfileTextures unpackTextures(Property property) { return upstream.unpackTextures(property); }
    @Override public ProfileResult fetchProfile(UUID id, boolean secure) { return upstream.fetchProfile(id, secure); }
    @Override public String getSecurePropertyValue(Property property) throws InsecurePublicKeyException {
        return upstream.getSecurePropertyValue(property);
    }
}
