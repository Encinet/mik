package org.encinet.mik.module.space;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaceConfigurationLoaderTest {

    private static final List<String> EXAMPLES = List.of(
            "examples/non-euclidean/infinite-stairwell.yml",
            "examples/non-euclidean/endless-corridor.yml",
            "examples/non-euclidean/impossible-corner.yml",
            "examples/non-euclidean/looping-fall.yml");

    @Test
    void bundledConfigurationIsAlwaysLoadable() throws Exception {
        SpaceConfiguration configuration = parseResource("non-euclidean-spaces.yml");

        assertTrue(configuration.enabled());
        assertTrue(configuration.links().isEmpty());
    }

    @Test
    void everyBundledImmersiveExampleIsLoadable() throws Exception {
        for (String resource : EXAMPLES) {
            SpaceConfiguration configuration = parseResource(resource);
            assertTrue(configuration.enabled(), resource);
            assertEquals(1, configuration.links().size(), resource);
        }
    }

    @Test
    void parsesNamedSurfacesAndFirstEntranceLink() throws Exception {
        SpaceConfiguration configuration = parse("""
                version: 3
                enabled: true
                surfaces:
                  upper:
                    world: world
                    area:
                      corner-a: [-1.0, 70.25, -8.0]
                      corner-b: [2.0, 73.75, -8.0]
                    through: north
                  lower:
                    world: world
                    area:
                      corner-a: [-1.0, 62.25, 8.0]
                      corner-b: [2.0, 65.75, 8.0]
                    through: north
                links:
                  stair-loop:
                    connect: [upper, lower]
                    entrances: first
                """);

        SpaceLink link = configuration.links().getFirst();
        assertEquals("stair-loop", link.id());
        assertEquals("upper", link.first().id());
        assertEquals("lower", link.second().id());
        assertEquals(SpaceLinkEntrances.FIRST, link.entrances());
        assertEquals(3.0, link.first().aperture().width());
        assertEquals(3.5, link.first().aperture().height());
        assertEquals(new SpaceVector(0.0, 0.0, -1.0), link.first().frame().forward());
    }

    @Test
    void supportsVerticalAndArbitrarySurfaceAxes() throws Exception {
        SpaceLink link = parse("""
                version: 3
                surfaces:
                  floor:
                    world: world
                    area:
                      corner-a: [-2, 40, -2]
                      corner-b: [2, 40, 2]
                    through: down
                    up: north
                  wall:
                    world: world
                    area:
                      corner-a: [8, 62, -2]
                      corner-b: [8, 66, 2]
                    through: [1, 0, 0]
                    up: [0, 1, 0]
                links:
                  folded-space:
                    connect: [floor, wall]
                    entrances: first
                """).links().getFirst();

        assertEquals(new SpaceVector(0.0, -1.0, 0.0),
                link.first().frame().forward());
        assertEquals(new SpaceVector(1.0, 0.0, 0.0),
                link.second().frame().forward());
    }

    @Test
    void reportedJumpDoorSupportsConfiguredDirectionFromBothSurfaces() throws Exception {
        SpaceConfiguration configuration = parse("""
                version: 3
                surfaces:
                  jump-door-1:
                    world: world
                    area:
                      corner-a: [329.5, 80.0, 1363.0]
                      corner-b: [329.5, 83.0, 1366.0]
                    through: west
                  jump-door-2:
                    world: world
                    area:
                      corner-a: [324.0, 72.5, 1363.0]
                      corner-b: [321.0, 72.5, 1366.0]
                    through: up
                links:
                  jump-door:
                    connect: [jump-door-1, jump-door-2]
                    entrances: both
                """);
        SpaceLink link = configuration.links().getFirst();
        assertEquals(SpaceLinkEntrances.BOTH, link.entrances());
        SpaceFrame entrance = link.first().frame();
        SpaceTransition transition = SpaceNetwork.of(configuration.links()).trace(
                "world",
                entrance.fromLocalPoint(new SpaceVector(0.0, 0.0, -0.2)),
                entrance.fromLocalPoint(new SpaceVector(0.0, 0.0, 0.2)),
                new SpaceVector(-1.0, 0.0, 0.0),
                new SpaceVector(-1.2, 0.3, 0.0)).orElseThrow();

        assertEquals(new SpaceVector(0.0, 1.2, -0.3), transition.velocity());
        assertEquals(new SpaceVector(0.0, 1.0, 0.0), transition.lookDirection());

        SpaceFrame upwardFloor = link.second().frame();
        SpaceEntityShape playerShape = SpaceEntityShape.from(
                new BoundingBox(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3),
                SpaceVector.ZERO);
        for (double localX : new double[]{-1.5, 0.0, 1.5}) {
            for (double localY : new double[]{-1.5, 0.0, 1.5}) {
                SpaceTransition reverse = SpaceNetwork.of(configuration.links()).trace(
                        "world",
                        upwardFloor.fromLocalPoint(
                                new SpaceVector(localX, localY, -0.2)),
                        upwardFloor.fromLocalPoint(
                                new SpaceVector(localX, localY, 0.2)),
                        upwardFloor.forward(),
                        upwardFloor.forward().multiply(0.8)).orElseThrow();
                assertEquals("jump-door:second-forward-to-first-forward",
                        reverse.routeId());
                assertEquals(new SpaceVector(-1.0, 0.0, 0.0),
                        reverse.lookDirection());
                assertEquals(new SpaceVector(-0.8, 0.0, 0.0),
                        reverse.velocity());
                SpaceAperturePlacement.Result placement = SpaceAperturePlacement.fit(
                        reverse.transform().destination(),
                        reverse.destinationAperture(),
                        reverse.entryPosition(),
                        reverse.position(),
                        playerShape).orElseThrow();
                SpaceVector fitted = reverse.transform().destination()
                        .toLocalPoint(placement.entry());

                assertTrue(fitted.x() >= -1.18 - 1.0E-9);
                assertTrue(fitted.x() <= 1.18 + 1.0E-9);
                assertTrue(fitted.y() >= -1.48 - 1.0E-9);
                assertTrue(fitted.y() <= -0.32 + 1.0E-9);
            }
        }
    }

    @Test
    void parsesAnExplicitUpwardHorizontalLink() throws Exception {
        SpaceLink link = parse("""
                version: 3
                surfaces:
                  lower:
                    world: world
                    area: { corner-a: [-2, 40, -2], corner-b: [2, 40, 2] }
                    through: up
                    up: north
                  upper:
                    world: world
                    area: { corner-a: [-2, 72, -2], corner-b: [2, 72, 2] }
                    through: up
                    up: north
                links:
                  upward-loop: { connect: [lower, upper], entrances: first }
                """).links().getFirst();

        assertEquals(new SpaceVector(0.0, 1.0, 0.0),
                link.first().frame().forward());
        assertEquals(new SpaceVector(0.0, 1.0, 0.0),
                link.second().frame().forward());
    }

    @Test
    void rejectsOlderFormatInsteadOfInterpretingIt() {
        SpaceConfigurationException error = assertThrows(
                SpaceConfigurationException.class,
                () -> parse("""
                        version: 1
                        connections: {}
                        """));

        assertTrue(error.problems().stream().anyMatch(problem -> problem.contains("unsupported")));
        assertTrue(error.problems().stream().anyMatch(
                problem -> problem.contains("unknown key 'connections'")));
    }

    @Test
    void requiresAnExplicitEntranceSelection() {
        SpaceConfigurationException missing = assertThrows(
                SpaceConfigurationException.class,
                () -> parse("""
                        version: 3
                        surfaces:
                          a:
                            world: world
                            area: { corner-a: [-1, 64, 0], corner-b: [2, 67, 0] }
                            through: south
                          b:
                            world: world
                            area: { corner-a: [7, 64, 0], corner-b: [10, 67, 0] }
                            through: south
                        links:
                          ambiguous: { connect: [a, b] }
                        """));

        assertTrue(missing.problems().stream().anyMatch(
                problem -> problem.contains("links.ambiguous.entrances must be first or both")));
    }

    @Test
    void rejectsCrossWorldSurfaces() {
        SpaceConfigurationException error = assertThrows(
                SpaceConfigurationException.class,
                () -> parse("""
                        version: 3
                        surfaces:
                          a:
                            world: world
                            area: { corner-a: [-1, 64, 0], corner-b: [2, 67, 0] }
                            through: south
                          b:
                            world: world_nether
                            area: { corner-a: [6, 64, 0], corner-b: [10, 67, 0] }
                            through: south
                        links:
                          broken:
                            connect: [a, b]
                            entrances: first
                        """));

        assertTrue(error.problems().stream().anyMatch(
                problem -> problem.contains("same world")));
    }

    @Test
    void rejectsDifferentAreaDimensionsWithinOneLink() {
        SpaceConfigurationException error = assertThrows(
                SpaceConfigurationException.class,
                () -> parse("""
                        version: 3
                        surfaces:
                          a:
                            world: world
                            area: { corner-a: [-1, 64, 0], corner-b: [2, 67, 0] }
                            through: south
                          b:
                            world: world
                            area: { corner-a: [6, 64, 0], corner-b: [10, 67, 0] }
                            through: south
                        links:
                          broken: { connect: [a, b], entrances: first }
                        """));

        assertTrue(error.problems().stream().anyMatch(
                problem -> problem.contains("identical aperture dimensions")));
    }

    @Test
    void rejectsASeamClaimedByMultipleLinks() {
        SpaceConfigurationException error = assertThrows(
                SpaceConfigurationException.class,
                () -> parse("""
                        version: 3
                        surfaces:
                          a:
                            world: world
                            area: { corner-a: [-1, 64, 0], corner-b: [2, 67, 0] }
                            through: south
                          b:
                            world: world
                            area: { corner-a: [7, 64, 0], corner-b: [10, 67, 0] }
                            through: south
                          c:
                            world: world
                            area: { corner-a: [15, 64, 0], corner-b: [18, 67, 0] }
                            through: south
                        links:
                          first: { connect: [a, b], entrances: first }
                          second: { connect: [a, c], entrances: first }
                        """));

        assertTrue(error.problems().stream().anyMatch(
                problem -> problem.contains("reuses surface 'a'")));
    }

    @Test
    void rejectsUnlinkedSurfacesAndUnknownSettings() {
        SpaceConfigurationException error = assertThrows(
                SpaceConfigurationException.class,
                () -> parse("""
                        version: 3
                        surfaces:
                          orphan:
                            world: world
                            area: { corner-a: [-1, 64, 0], corner-b: [2, 67, 0] }
                            through: south
                            sound: portal
                        links: {}
                        """));

        assertTrue(error.problems().stream().anyMatch(
                problem -> problem.contains("unknown key 'sound'")));
        assertTrue(error.problems().stream().anyMatch(
                problem -> problem.contains("is not connected")));
    }

    private SpaceConfiguration parseResource(String resource) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resource)),
                StandardCharsets.UTF_8)) {
            yaml.load(reader);
        }
        return new SpaceConfigurationLoader().parse(yaml);
    }

    private SpaceConfiguration parse(String source) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(source);
        return new SpaceConfigurationLoader().parse(yaml);
    }
}
