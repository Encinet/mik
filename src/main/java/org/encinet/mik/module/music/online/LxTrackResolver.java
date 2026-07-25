package org.encinet.mik.module.music.online;

import org.encinet.mik.module.music.catalog.TrackTarget;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
interface LxTrackResolver {

    CompletableFuture<String> resolve(TrackTarget.Lx target);
}
