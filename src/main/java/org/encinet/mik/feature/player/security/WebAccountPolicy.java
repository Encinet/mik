package org.encinet.mik.feature.player.security;

/** Public account policy only; never exposes password hashes or credential material. */
public record WebAccountPolicy(String role, long credentialsChangedAt) { }
