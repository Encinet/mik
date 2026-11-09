package org.encinet.mik.feature.server.auth;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.services.ProfileActionType;
import com.mojang.authlib.services.ProfileResult;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

final class AuthenticationTestSupport {
    static final InetAddress ADDRESS = address(new byte[]{(byte) 192, 0, 2, 10});
    static final InetAddress OTHER_ADDRESS = address(new byte[]{(byte) 192, 0, 2, 11});

    static InetAddress address(byte[] bytes) {
        try { return InetAddress.getByAddress(bytes); }
        catch (java.net.UnknownHostException impossible) { throw new AssertionError(impossible); }
    }

    static ProfileResult profile(UUID id, String name) {
        var properties = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", "skin", "signature")));
        return new ProfileResult(new GameProfile(id, name, properties), Set.of(ProfileActionType.USING_BANNED_SKIN));
    }

    static final class MutableClock extends Clock {
        private long millis = 1_800_000_000_000L;

        void advance(long millis) { this.millis += millis; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }
}
