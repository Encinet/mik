package org.encinet.mik.shared.menu.ui;

import java.util.ArrayList;
import java.util.List;
import static org.encinet.mik.shared.menu.ui.UiIcon.Part.*;

/** One flat family; names identify shapes, while each caller supplies its own localized meaning. */
public final class UiIcons {
    public static final UiIcon CROSS = UiIcon.of(line(6,6,18,18), line(18,6,6,18));
    public static final UiIcon REFRESH = combine(arc(12,12,8,30,300), path(19,3,19,8,14,8));
    public static final UiIcon UNDO = new UiIcon(REFRESH.frames().stream().map(frame -> new UiIcon.Frame(frame.parts().stream()
            .map(part -> new UiIcon.Part(24-part.x(), part.y(), part.width(), part.height(), 180-part.angle(), part.alpha())).toList())).toList(), 2, 0);
    public static final UiIcon CHEVRON_LEFT = path(15,5,8,12,15,19);
    public static final UiIcon CHEVRON_RIGHT = path(9,5,16,12,9,19);
    public static final UiIcon CHEVRON_UP = path(5,15,12,8,19,15);
    public static final UiIcon CHEVRON_DOWN = path(5,9,12,16,19,9);
    public static final UiIcon ADD = UiIcon.of(line(12,4,12,20), line(4,12,20,12));
    public static final UiIcon REMOVE = UiIcon.of(line(4,12,20,12));
    public static final UiIcon CHECK = path(4,12,9,17,20,6);
    public static final UiIcon COPY = combine(path(9,3,20,3,20,14), path(4,8,15,8,15,21,4,21,4,8));
    public static final UiIcon EXTERNAL_LINK = combine(path(10,5,5,5,5,20,19,20,19,14), path(11,13,21,3), path(14,3,21,3,21,10));
    public static final UiIcon MORE = UiIcon.of(rectangle(3,11,3,3), rectangle(10.5,11,3,3), rectangle(18,11,3,3));
    public static final UiIcon SEARCH = combine(arc(10.5,10.5,6,45,360), path(14.75,14.75,20.5,20.5));
    public static final UiIcon SETTINGS = combine(UiIcon.of(line(3,6,21,6),line(3,12,21,12),line(3,18,21,18)),
            UiIcon.of(rectangle(7,3,3,6),rectangle(15,9,3,6),rectangle(8,15,3,6)));
    public static final UiIcon DELETE = combine(path(6,7,6,20,18,20,18,7), path(4,6,20,6), path(9,5,9,3,15,3,15,5));
    public static final UiIcon INSERT_BEFORE = combine(path(4,4,20,4), path(12,21,12,8), path(7,13,12,8,17,13));
    public static final UiIcon BOX = path(4,4,20,4,20,20,4,20,4,4);
    public static final UiIcon CHECKED_BOX = combine(BOX,path(7,12,10.5,15.5,17,8.5));
    public static final UiIcon INFO = combine(path(3,3,21,3,21,21,3,21,3,3), UiIcon.of(line(12,10,12,17),rectangle(11,6,2,2)));
    public static final UiIcon WARNING = combine(path(12,3,22,20,2,20,12,3), UiIcon.of(line(12,9,12,14),rectangle(11,16,2,2)));
    public static final UiIcon ERROR = combine(path(12,2,22,12,12,22,2,12,12,2), UiIcon.of(line(8,8,16,16),line(16,8,8,16)));
    public static final UiIcon LOADING = loading();
    public static final UiIcon ACTIVITY_BARS = activity();
    public static final UiIcon PLAY = path(8,5,19,12,8,19,8,5);
    public static final UiIcon PAUSE = UiIcon.of(rectangle(6,5,4,14),rectangle(14,5,4,14));
    public static final UiIcon PREVIOUS = combine(path(17,5,7,12,17,19,17,5),path(4,5,4,19));
    public static final UiIcon NEXT = combine(path(7,5,17,12,7,19,7,5),path(20,5,20,19));
    public static final UiIcon STOP = UiIcon.of(rectangle(6,6,12,12));
    public static final UiIcon VOLUME = combine(path(5,10,8,10,13,5,13,19,8,14,5,14,5,10),arc(11,12,8,-35,70));
    public static final UiIcon MUTE = combine(path(4,10,7,10,12,5,12,19,7,14,4,14,4,10),UiIcon.of(line(16,9,22,15),line(22,9,16,15)));
    public static final UiIcon QUEUE = UiIcon.of(line(9,6,21,6),line(9,12,21,12),line(9,18,21,18),
            rectangle(3,5,2,2),rectangle(3,11,2,2),rectangle(3,17,2,2));

    private static UiIcon path(double... coordinates) {
        List<UiIcon.Part> parts = new ArrayList<>();
        int points = coordinates.length / 2;
        boolean closed = Math.hypot(coordinates[0]-coordinates[coordinates.length-2],
                coordinates[1]-coordinates[coordinates.length-1]) < 1e-6;
        for (int i = 1; i < points; i++) {
            double x1 = coordinates[(i-1)*2], y1 = coordinates[(i-1)*2+1];
            double x2 = coordinates[i*2], y2 = coordinates[i*2+1];
            var stroke = line(x1,y1,x2,y2);
            double before = i > 1 || closed ? join(coordinates, i-1, closed) : 0;
            double after = i < points-1 || closed ? join(coordinates, i, closed) : 0;
            double shift = (after-before)/2, radians = Math.toRadians(stroke.angle());
            parts.add(new UiIcon.Part(stroke.x()+Math.cos(radians)*shift, stroke.y()+Math.sin(radians)*shift,
                    stroke.width()+before+after, stroke.height(), stroke.angle(), stroke.alpha()));
        }
        return new UiIcon(List.of(new UiIcon.Frame(parts)),2,0);
    }

    /** Extend neighbouring strokes to their bounded miter instead of leaving pinholes at corners. */
    private static double join(double[] coordinates, int point, boolean closed) {
        int count = coordinates.length/2 - (closed ? 1 : 0);
        int previous = Math.floorMod(point-1,count), current = point%count, next = (point+1)%count;
        double ax = coordinates[current*2]-coordinates[previous*2], ay = coordinates[current*2+1]-coordinates[previous*2+1];
        double bx = coordinates[next*2]-coordinates[current*2], by = coordinates[next*2+1]-coordinates[current*2+1];
        double cosine = Math.clamp((ax*bx+ay*by)/(Math.hypot(ax,ay)*Math.hypot(bx,by)),-1,1);
        return Math.min(2, Math.tan(Math.acos(cosine)/2));
    }

    /** At most 24 degrees per chord: less than .18 UI radial error for this family's curves. */
    private static UiIcon arc(double x, double y, double radius, double start, double sweep) {
        int segments = (int)Math.ceil(Math.abs(sweep)/24);
        double[] points = new double[(segments+1)*2];
        for (int i = 0; i <= segments; i++) {
            double angle = Math.toRadians(start+sweep*i/segments);
            points[i*2] = x+radius*Math.cos(angle);
            points[i*2+1] = y+radius*Math.sin(angle);
        }
        return path(points);
    }
    private static UiIcon combine(UiIcon... icons) {
        return new UiIcon(List.of(new UiIcon.Frame(java.util.Arrays.stream(icons)
                .flatMap(icon -> icon.frames().getFirst().parts().stream()).toList())),2,0);
    }
    private static UiIcon loading() {
        var frames = new ArrayList<UiIcon.Frame>();
        for (int frame = 0; frame < 8; frame++) {
            var parts = new ArrayList<UiIcon.Part>();
            for (int i = 0; i < 8; i++) {
                double angle = Math.toRadians(i * 45);
                parts.add(new UiIcon.Part(12 + 8*Math.sin(angle), 12 - 8*Math.cos(angle), 2, 4, i*45,
                        .3 + Math.floorMod(i-frame,8)*.1));
            }
            frames.add(new UiIcon.Frame(parts));
        }
        return new UiIcon(frames,2,0);
    }
    private static UiIcon activity() {
        var frames = new ArrayList<UiIcon.Frame>();
        for (int frame = 0; frame < 16; frame++) {
            var parts = new ArrayList<UiIcon.Part>();
            for (int i = 0; i < 3; i++) {
                double height = 7 + 9*(1 + Math.sin(frame*Math.PI/8 + i*2.3))/2;
                parts.add(rectangle(4 + i*6.5,20-height,3,height));
            }
            frames.add(new UiIcon.Frame(parts));
        }
        return new UiIcon(frames,2,0);
    }
    private UiIcons() { }
}
