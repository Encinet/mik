package org.encinet.mik.feature.world.plot;

import java.util.Objects;
import java.util.UUID;
import org.encinet.mik.shared.i18n.Message;

/** A named teleport destination with plain directory text and optional MiniMessage arrival titles. */
record PlotDestination(UUID id, UUID plotId, String name, String description, boolean publicAccess,
                       double x, double y, double z, float yaw, float pitch, String title, String subtitle) {
    static final int NAME_LIMIT = 48;
    static final int DESCRIPTION_LIMIT = 512;

    PlotDestination {
        Objects.requireNonNull(id);
        Objects.requireNonNull(plotId);
        name = Objects.requireNonNull(name).strip();
        description = Objects.requireNonNull(description).replace("\r\n", "\n").replace('\r', '\n').strip();
        validateText(name, description);
        title = Objects.requireNonNull(title).strip();
        subtitle = Objects.requireNonNull(subtitle).strip();
        PlotDestinationTitles.validate(title, subtitle);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch))
            throw new IllegalArgumentException("Destination coordinates must be finite");
    }

    static void validateText(String name, String description) {
        if (name.isBlank() || name.codePointCount(0, name.length()) > NAME_LIMIT
                || name.codePoints().anyMatch(PlotDestination::lineBreakOrControl))
            throw new PlotProblem(Message.PLOT_DESTINATION_ERROR_NAME);
        if (description.codePointCount(0, description.length()) > DESCRIPTION_LIMIT
                || description.codePoints().anyMatch(point -> point != '\n' && lineBreakOrControl(point)))
            throw new PlotProblem(Message.PLOT_DESTINATION_ERROR_DESCRIPTION);
    }

    private static boolean lineBreakOrControl(int point) {
        int type = Character.getType(point);
        return Character.isISOControl(point) || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }

    PlotDestination details(String name, String description) {
        return new PlotDestination(id, plotId, name, description, publicAccess, x, y, z, yaw, pitch, title, subtitle);
    }

    PlotDestination published(boolean value) {
        return new PlotDestination(id, plotId, name, description, value, x, y, z, yaw, pitch, title, subtitle);
    }

    PlotDestination titles(String title, String subtitle) {
        return new PlotDestination(id, plotId, name, description, publicAccess, x, y, z, yaw, pitch, title, subtitle);
    }

    int blockX() { return (int) Math.floor(x); }
    int blockY() { return (int) Math.floor(y); }
    int blockZ() { return (int) Math.floor(z); }
}
