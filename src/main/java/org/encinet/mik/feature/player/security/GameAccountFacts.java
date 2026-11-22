package org.encinet.mik.feature.player.security;

public record GameAccountFacts(boolean localCustodian, long credentialsChangedAt, boolean loginMatches) { }
