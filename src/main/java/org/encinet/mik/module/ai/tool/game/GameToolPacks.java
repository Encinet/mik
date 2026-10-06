package org.encinet.mik.module.ai.tool.game;

import org.encinet.mik.module.ai.tool.AiToolPack;

import java.util.List;

/** Creates the independent game capability packs from one consistent snapshot. */
public final class GameToolPacks {
    private GameToolPacks() {
    }

    public static List<AiToolPack> create(AiGameSnapshot snapshot) {
        return List.of(
                ServerToolPack.create(snapshot),
                PlayerToolPack.create(snapshot),
                WorldToolPack.create(snapshot),
                MinecraftToolPack.create(snapshot));
    }
}
