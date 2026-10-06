package org.encinet.mik.module.elevator;

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
        int fallback = floors.getFirst();
        World world = source.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return new Estimate(fallback, true);
        List<Sample> samples = new ArrayList<>();
        List<Probe> probes = new ArrayList<>();
        sample(source, PRIMARY_RADII, samples, probes);
        int reference = reference(samples, fallback);
        if (!stable(samples) || highestFloor(floors, reference)) {
            sample(source, EXTENDED_RADII, samples, probes);
            reference = reference(samples, fallback);
        }
        boolean stable = stable(samples);
        if (!highestFloor(floors, reference)) return new Estimate(reference, stable);
        int minimumSupport = Math.max(6, (probes.size() + 3) / 4);
        for (int floor : floors.stream().limit(16).toList()) {
            if (floor >= floors.getLast()) break;
            List<Sample> entrances = new ArrayList<>();
            for (Probe probe : probes) {
                Sample sample = entrance(world, probe, floor);
                if (sample != null) entrances.add(sample);
            }
            if (entrances.size() >= minimumSupport
                    && entrances.stream().map(Sample::radius).distinct().count() >= 2
                    && entrances.stream().filter(sample -> sample.radius() >= 16).count() >= 6)
                return new Estimate(reference(entrances, floor), true);
        }
        int groundReference = reference;
        long naturalEvidence = samples.stream().filter(sample -> sample.natural()
                && Math.abs((long) sample.height() - groundReference) <= 4).count();
        return naturalEvidence >= 3 && fartherSupport(samples, reference)
                ? new Estimate(reference, stable) : new Estimate(fallback, false);
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

    private static void sample(Block source, int[] radii, List<Sample> samples, List<Probe> probes) {
        World world = source.getWorld();
        for (int radius : radii) {
            for (int offsetX : new int[]{-radius, 0, radius}) {
                for (int offsetZ : new int[]{-radius, 0, radius}) {
                    if (offsetX == 0 && offsetZ == 0) continue;
                    int blockX = source.getX() + offsetX;
                    int blockZ = source.getZ() + offsetZ;
                    if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) continue;
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
            }
        }
    }

    private static Sample entrance(World world, Probe probe, int floor) {
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
