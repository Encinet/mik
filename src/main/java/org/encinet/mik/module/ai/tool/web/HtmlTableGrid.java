package org.encinet.mik.module.ai.tool.web;

import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Flattens direct HTML table rows while retaining bounded colspan/rowspan placement. */
final class HtmlTableGrid {
    private HtmlTableGrid() {
    }

    static List<List<Element>> rows(Element table, int maximumRows, int maximumColumns) {
        List<Element> sourceRows = directRows(table).stream().limit(maximumRows).toList();
        int[] remainingRowSpans = new int[maximumColumns];
        List<List<Element>> result = new ArrayList<>();
        for (Element row : sourceRows) {
            boolean[] occupied = new boolean[maximumColumns];
            for (int column = 0; column < maximumColumns; column++) {
                if (remainingRowSpans[column] > 0) {
                    occupied[column] = true;
                    remainingRowSpans[column]--;
                }
            }
            Element[] values = new Element[maximumColumns];
            for (Element cell : row.children()) {
                if (!(cell.normalName().equals("th") || cell.normalName().equals("td"))) {
                    continue;
                }
                int columnSpan = Math.clamp(positiveAttribute(cell, "colspan", 1),
                        1, maximumColumns);
                int start = availableRun(occupied, columnSpan);
                if (start < 0 && columnSpan > 1) {
                    columnSpan = 1;
                    start = availableRun(occupied, columnSpan);
                }
                if (start < 0) {
                    break;
                }
                values[start] = cell;
                int rowSpan = positiveAttribute(cell, "rowspan", 1);
                if (cell.attr("rowspan").strip().equals("0")) {
                    rowSpan = sourceRows.size();
                }
                rowSpan = Math.clamp(rowSpan, 1, maximumRows);
                for (int column = start;
                     column < start + columnSpan && column < maximumColumns; column++) {
                    occupied[column] = true;
                    remainingRowSpans[column] = Math.max(
                            remainingRowSpans[column], rowSpan - 1);
                }
            }
            int width = 0;
            for (int column = 0; column < maximumColumns; column++) {
                if (occupied[column] || values[column] != null) {
                    width = column + 1;
                }
            }
            if (width > 0) {
                result.add(new ArrayList<>(Arrays.asList(values).subList(0, width)));
            }
        }
        return result;
    }

    private static int availableRun(boolean[] occupied, int length) {
        for (int start = 0; start + length <= occupied.length; start++) {
            boolean available = true;
            for (int column = start; column < start + length; column++) {
                if (occupied[column]) {
                    available = false;
                    break;
                }
            }
            if (available) {
                return start;
            }
        }
        return -1;
    }

    private static int positiveAttribute(Element element, String name, int fallback) {
        try {
            int value = Integer.parseInt(element.attr(name));
            return value > 0 ? value : fallback;
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static List<Element> directRows(Element table) {
        List<Element> rows = new ArrayList<>();
        for (Element child : table.children()) {
            if (child.normalName().equals("tr")) {
                rows.add(child);
                continue;
            }
            if (!(child.normalName().equals("thead")
                    || child.normalName().equals("tbody")
                    || child.normalName().equals("tfoot"))) {
                continue;
            }
            for (Element row : child.children()) {
                if (row.normalName().equals("tr")) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }
}
