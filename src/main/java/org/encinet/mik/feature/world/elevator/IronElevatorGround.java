package org.encinet.mik.feature.world.elevator;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class IronElevatorGround {

    private static final int[] PRIMARY_RADII = {4, 8, 16, 32, 64, 128};
    private static final int[] EXTENDED_RADII = {256, 512};

    record Sample(int height, int radius, boolean natural) {
        Sample(int height, int radius) {
            this(height, radius, true);
        }
    }

    private record Probe(int blockX, int blockZ, int radius) {
    }

    record Estimate(int height, boolean stable) {
    }

    private IronElevatorGround() {
    }

    static int estimate(Block source, int fallback) {
        return measure(source, fallback).height();
    }

    static Estimate measure(Block source, int fallback) {
        return measure(source, List.of(fallback));
    }

    static Estimate measure(Block source, List<Integer> floors) {
        Measurement measurement = new Measurement(source, floors);
        while (!measurement.step()) { }
        return measurement.result();
    }

    /** One surface or entrance probe per step; numbering never gates route discovery. */
    static final class Measurement {
        private final Block source;
        private final World world;
        private final List<Integer> floors;
        private final List<Sample> samples = new ArrayList<>();
        private final List<Probe> probes = new ArrayList<>();
        private final List<Sample> entrances = new ArrayList<>();
        private int ring;
        private int point;
        private int entranceFloor;
        private int entranceProbe;
        private int reference;
        private boolean surfaceDone;
        private Estimate result;

        Measurement(Block source, List<Integer> floors) {
            this.source = source;
            this.world = source.getWorld();
            this.floors = List.copyOf(floors);
            if (world.getEnvironment() != World.Environment.NORMAL)
                result = new Estimate(floors.getFirst(), true);
        }

        Estimate result() { return result; }

        boolean step() {
            if (result != null) return true;
            if (!surfaceDone) {
                int radius = ring < PRIMARY_RADII.length ? PRIMARY_RADII[ring]
                        : EXTENDED_RADII[ring - PRIMARY_RADII.length];
                int slot = point < 4 ? point : point + 1;
                probeSurface(source, radius, (slot / 3 - 1) * radius, (slot % 3 - 1) * radius,
                        samples, probes);
                if (++point < 8) return false;
                point = 0;
                ring++;
                reference = reference(samples, floors.getFirst());
                if (ring == PRIMARY_RADII.length && stable(samples) && !highestFloor(floors, reference))
                    surfaceDone = true;
                else if (ring == PRIMARY_RADII.length + EXTENDED_RADII.length) surfaceDone = true;
                if (surfaceDone && !highestFloor(floors, reference)) result = new Estimate(reference, stable(samples));
                return result != null;
            }
            if (entranceFloor < Math.min(16, floors.size()) && floors.get(entranceFloor) < floors.getLast()) {
                if (entranceProbe < probes.size()) {
                    Sample sample = entrance(world, probes.get(entranceProbe++), floors.get(entranceFloor));
                    if (sample != null) entrances.add(sample);
                    return false;
                }
                int minimumSupport = Math.max(6, (probes.size() + 3) / 4);
                if (entrances.size() >= minimumSupport
                        && entrances.stream().map(Sample::radius).distinct().count() >= 2
                        && entrances.stream().filter(sample -> sample.radius() >= 16).count() >= 6) {
                    result = new Estimate(reference(entrances, floors.get(entranceFloor)), true);
                    return true;
                }
                entranceFloor++;
                entranceProbe = 0;
                entrances.clear();
                return false;
            }
            long naturalEvidence = samples.stream().filter(sample -> sample.natural()
                    && Math.abs((long) sample.height() - reference) <= 4).count();
            result = naturalEvidence >= 3 && fartherSupport(samples, reference)
                    ? new Estimate(reference, stable(samples)) : new Estimate(floors.getFirst(), false);
            return true;
        }
    }

    private static boolean fartherSupport(List<Sample> samples, int reference) {
        for (int radius : EXTENDED_RADII) {
            if (samples.stream().filter(sample -> sample.radius() == radius
                    && Math.abs((long) sample.height() - reference) <= 4).count() < 3) return false;
        }
        return true;
    }

    private static boolean stable(List<Sample> samples) {
        return samples.size() >= 8 && samples.stream().map(Sample::radius).distinct().count() >= 2;
    }

    private static boolean highestFloor(List<Integer> floors, int reference) {
        return floors.size() >= 3 && IronElevatorFloors.groundIndex(floors, reference) == floors.size() - 1;
    }

    private static void probeSurface(Block source, int radius, int offsetX, int offsetZ,
                                     List<Sample> samples, List<Probe> probes) {
        World world = source.getWorld();
        int blockX = source.getX() + offsetX;
        int blockZ = source.getZ() + offsetZ;
        if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) return;
        probes.add(new Probe(blockX, blockZ, radius));
        int surface = world.getHighestBlockYAt(blockX, blockZ, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        for (int depth = 0; depth <= 16 && surface - depth >= world.getMinHeight(); depth++) {
            Block block = world.getBlockAt(blockX, surface - depth, blockZ);
            Material material = block.getType();
            if (foliage(material) || material == Material.AIR
                    || material == Material.CAVE_AIR || material == Material.VOID_AIR) continue;
            if (material != Material.WATER && material != Material.LAVA
                    && material != Material.BUBBLE_COLUMN
                    && block.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL))
                samples.add(new Sample(surface - depth, radius, natural(material)));
            break;
        }
    }

    private static Sample entrance(World world, Probe probe, int floor) {
        if (!world.isChunkLoaded(probe.blockX() >> 4, probe.blockZ() >> 4)) return null;
        for (int offset : new int[]{0, -1, 1, -2, 2}) {
            int height = floor + offset;
            if (height < world.getMinHeight() || height + 2 >= world.getMaxHeight()) continue;
            Block support = world.getBlockAt(probe.blockX(), height, probe.blockZ());
            Material material = support.getType();
            if (foliage(material) || material == Material.IRON_BLOCK
                    || !support.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL)) continue;
            Location feet = new Location(world, probe.blockX() + 0.5, height + 1, probe.blockZ() + 0.5);
            if (IronElevatorLanding.isSafe(feet, IronElevatorLanding.DEFAULT_SIZE, body -> false))
                return new Sample(height, probe.radius(), natural(material));
        }
        return null;
    }

    private static boolean natural(Material material) {
        return switch (material) {
            case GRASS_BLOCK, DIRT, COARSE_DIRT, ROOTED_DIRT, DIRT_PATH, FARMLAND,
                    PODZOL, MYCELIUM, MUD, CLAY, SAND, RED_SAND, GRAVEL, SNOW_BLOCK, MOSS_BLOCK -> true;
            default -> false;
        };
    }

    static int reference(List<Sample> samples, int fallback) {
        if (samples.isEmpty()) return fallback;
        List<Sample> sorted = samples.stream().sorted(Comparator.comparingInt(Sample::height)).toList();
        int minimumSupport = Math.max(3, (sorted.size() + 3) / 4);
        for (int start = 0; start < sorted.size(); start++) {
            int end = start;
            while (end < sorted.size() && (long) sorted.get(end).height() - sorted.get(start).height() <= 4) end++;
            List<Sample> cluster = sorted.subList(start, end);
            if (cluster.size() >= minimumSupport && cluster.stream().map(Sample::radius).distinct().count() >= 2)
                return cluster.get((cluster.size() - 1) / 2).height();
        }
        return sorted.get((sorted.size() - 1) / 2).height();
    }

    private static boolean foliage(Material material) {
        String name = material.name();
        return name.endsWith("_LEAVES") || name.endsWith("_LOG") || name.endsWith("_WOOD")
                || name.endsWith("_STEM") || name.endsWith("_HYPHAE");
    }
}
