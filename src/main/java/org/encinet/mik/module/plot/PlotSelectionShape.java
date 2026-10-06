package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.plot.PlotGeometry.Cell;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

record PlotSelectionShape(UUID world, PlotSelection cuboid, Set<Cell> cells) {
    PlotSelectionShape {
        Objects.requireNonNull(world, "world");
        cells = Set.copyOf(cells);
        if (cuboid != null && (!world.equals(cuboid.first().world()) || !cells.isEmpty()))
            throw new IllegalArgumentException("Invalid selection shape");
    }

    static PlotSelectionShape of(PlotSelection cuboid) {
        return new PlotSelectionShape(cuboid.first().world(), cuboid, Set.of());
    }

    static PlotSelectionShape of(PlotPosition first, PlotPosition second) {
        if (first == null || second == null || !first.world().equals(second.world()))
            throw new PlotProblem(Message.PLOT_ERROR_SELECTION);
        return of(new PlotSelection(first, second));
    }

    static PlotSelectionShape fromCells(UUID world, Set<Cell> cells) {
        if (cells.isEmpty()) return new PlotSelectionShape(world, null, cells);
        PlotSelection enclosing = PlotSelection.enclosing(world, cells);
        PlotSelection.Bounds bounds = enclosing.bounds();
        long width = bounds.width() / PlotGeometry.CELL;
        long height = bounds.height() / PlotGeometry.CELL;
        long depth = bounds.depth() / PlotGeometry.CELL;
        long count = cells.size();
        if (width <= count / height && width * height <= count / depth
                && width * height * depth == count)
            return of(enclosing);
        return new PlotSelectionShape(world, null, cells);
    }

    boolean composite() {
        return cuboid == null;
    }

    boolean empty() {
        return composite() && cells.isEmpty();
    }

    boolean contains(Cell cell) {
        if (composite()) return cells.contains(cell);
        PlotSelection.Bounds bounds = cuboid.bounds();
        int size = PlotGeometry.CELL;
        return cell.x() >= Math.floorDiv(bounds.minimumX(), size) && cell.x() <= Math.floorDiv(bounds.maximumX(), size)
                && cell.y() >= Math.floorDiv(bounds.minimumY(), size) && cell.y() <= Math.floorDiv(bounds.maximumY(), size)
                && cell.z() >= Math.floorDiv(bounds.minimumZ(), size) && cell.z() <= Math.floorDiv(bounds.maximumZ(), size);
    }

    boolean intersects(Set<Cell> other) {
        if (empty() || other.isEmpty()) return false;
        if (composite()) return !Collections.disjoint(cells, other);
        PlotSelection.Bounds bounds = cuboid.bounds();
        int size = PlotGeometry.CELL;
        int minimumX = Math.floorDiv(bounds.minimumX(), size), maximumX = Math.floorDiv(bounds.maximumX(), size);
        int minimumY = Math.floorDiv(bounds.minimumY(), size), maximumY = Math.floorDiv(bounds.maximumY(), size);
        int minimumZ = Math.floorDiv(bounds.minimumZ(), size), maximumZ = Math.floorDiv(bounds.maximumZ(), size);
        long width = (long) maximumX - minimumX + 1;
        long height = (long) maximumY - minimumY + 1;
        long depth = (long) maximumZ - minimumZ + 1;
        if (width > other.size() / height || width * height > other.size() / depth)
            return other.stream().anyMatch(this::contains);
        for (int coordinateX = minimumX; coordinateX <= maximumX; coordinateX++)
            for (int coordinateY = minimumY; coordinateY <= maximumY; coordinateY++)
                for (int coordinateZ = minimumZ; coordinateZ <= maximumZ; coordinateZ++)
                    if (other.contains(new Cell(coordinateX, coordinateY, coordinateZ))) return true;
        return false;
    }

    PlotSelection.Bounds bounds() {
        if (empty()) throw new PlotProblem(Message.PLOT_ERROR_SELECTION);
        return (composite() ? PlotSelection.enclosing(world, cells) : cuboid).bounds();
    }

    PlotSelection.Bounds alignedBounds() {
        if (composite()) return bounds();
        PlotSelection.Bounds raw = cuboid.bounds();
        int size = PlotGeometry.CELL;
        return new PlotSelection.Bounds(
                Math.floorDiv(raw.minimumX(), size) * size,
                Math.floorDiv(raw.minimumY(), size) * size,
                Math.floorDiv(raw.minimumZ(), size) * size,
                Math.floorDiv(raw.maximumX(), size) * size + size - 1,
                Math.floorDiv(raw.maximumY(), size) * size + size - 1,
                Math.floorDiv(raw.maximumZ(), size) * size + size - 1);
    }

    BigInteger alignedCellCount() {
        if (composite()) return BigInteger.valueOf(cells.size());
        PlotSelection.Bounds bounds = alignedBounds();
        return BigInteger.valueOf(bounds.width() / PlotGeometry.CELL)
                .multiply(BigInteger.valueOf(bounds.height() / PlotGeometry.CELL))
                .multiply(BigInteger.valueOf(bounds.depth() / PlotGeometry.CELL));
    }

    Set<Cell> alignedCells() {
        if (composite()) return cells;
        Cell first = Cell.at(cuboid.first().x(), cuboid.first().y(), cuboid.first().z());
        Cell second = Cell.at(cuboid.second().x(), cuboid.second().y(), cuboid.second().z());
        int minimumX = Math.min(first.x(), second.x()), maximumX = Math.max(first.x(), second.x());
        int minimumY = Math.min(first.y(), second.y()), maximumY = Math.max(first.y(), second.y());
        int minimumZ = Math.min(first.z(), second.z()), maximumZ = Math.max(first.z(), second.z());
        Set<Cell> selected = new HashSet<>();
        for (int cellX = minimumX; cellX <= maximumX; cellX++)
            for (int cellY = minimumY; cellY <= maximumY; cellY++)
                for (int cellZ = minimumZ; cellZ <= maximumZ; cellZ++)
                    selected.add(new Cell(cellX, cellY, cellZ));
        return Set.copyOf(selected);
    }

    PlotSelectionShape apply(PlotSelection brush, Operation operation) {
        if (!world.equals(brush.first().world())) throw new PlotProblem(Message.PLOT_ERROR_SELECTION);
        Set<Cell> next = new HashSet<>(operation == Operation.REPLACE ? Set.of() : alignedCells());
        if (operation == Operation.SUBTRACT) {
            PlotSelection.Bounds bounds = brush.bounds();
            Cell minimum = Cell.at(bounds.minimumX(), bounds.minimumY(), bounds.minimumZ());
            Cell maximum = Cell.at(bounds.maximumX(), bounds.maximumY(), bounds.maximumZ());
            next.removeIf(cell -> cell.x() >= minimum.x() && cell.x() <= maximum.x()
                    && cell.y() >= minimum.y() && cell.y() <= maximum.y()
                    && cell.z() >= minimum.z() && cell.z() <= maximum.z());
        } else next.addAll(of(brush).alignedCells());
        return fromCells(world, next);
    }

    enum Operation { ADD, SUBTRACT, REPLACE }
}
