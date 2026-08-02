package org.encinet.mik.module.world.regen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/** A detached, immutable NBT representation of one generated structure start. */
final class RegenStructureStartData {

    private final String identity;
    private final String type;
    private final byte[] serialized;

    private RegenStructureStartData(String identity, String type, byte[] serialized) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.type = Objects.requireNonNull(type, "type");
        this.serialized = Arrays.copyOf(serialized, serialized.length);
    }

    static RegenStructureStartData capture(
            String identity,
            String type,
            StructureStart start,
            ServerLevel source
    ) {
        CompoundTag tag = start.createTag(
                StructurePieceSerializationContext.fromLevel(source), start.getChunkPos());
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.write(tag, output);
            output.flush();
            return new RegenStructureStartData(identity, type, bytes.toByteArray());
        } catch (IOException error) {
            throw new IllegalStateException("Could not serialize generated structure " + identity, error);
        }
    }

    String identity() {
        return identity;
    }

    String type() {
        return type;
    }

    StructureStart load(ServerLevel target) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(serialized))) {
            CompoundTag tag = NbtIo.read(input);
            StructureStart loaded = StructureStart.loadStaticStart(
                    StructurePieceSerializationContext.fromLevel(target), tag, target.getSeed());
            if (loaded == null || !loaded.isValid()) {
                throw new IllegalStateException("Generated structure start is invalid: " + identity);
            }
            return loaded;
        } catch (IOException error) {
            throw new IllegalStateException("Could not deserialize generated structure " + identity, error);
        }
    }
}
