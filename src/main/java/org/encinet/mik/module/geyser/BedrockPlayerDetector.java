package org.encinet.mik.module.geyser;

import java.util.UUID;

/** Client-platform query shared by features that adapt their presentation for Bedrock. */
@FunctionalInterface
public interface BedrockPlayerDetector {

    boolean isBedrockPlayer(UUID playerId);
}
