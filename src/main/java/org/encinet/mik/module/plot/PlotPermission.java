package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public enum PlotPermission {
    PLACE("place", Category.BUILD, Message.PLOT_PERMISSION_PLACE),
    BREAK("break", Category.BUILD, Message.PLOT_PERMISSION_BREAK),
    BUCKET("bucket", Category.BUILD, Message.PLOT_PERMISSION_BUCKET),
    IGNITE("ignite", Category.BUILD, Message.PLOT_PERMISSION_IGNITE),
    MODIFY("modify", Category.BUILD, Message.PLOT_PERMISSION_MODIFY),
    HARVEST("harvest", Category.BUILD, Message.PLOT_PERMISSION_HARVEST),
    DOOR("door", Category.INTERACTION, Message.PLOT_PERMISSION_DOOR),
    SWITCH("switch", Category.INTERACTION, Message.PLOT_PERMISSION_SWITCH),
    WORKSTATION("workstation", Category.INTERACTION, Message.PLOT_PERMISSION_WORKSTATION),
    SIGN("sign", Category.INTERACTION, Message.PLOT_PERMISSION_SIGN),
    CONTAINER("container", Category.STORAGE, Message.PLOT_MENU_FLAG_CONTAINER),
    ENTITY_STORAGE("entity_storage", Category.STORAGE, Message.PLOT_PERMISSION_ENTITY_STORAGE),
    ITEM_PICKUP("item_pickup", Category.STORAGE, Message.PLOT_PERMISSION_ITEM_PICKUP),
    ITEM_DROP("item_drop", Category.STORAGE, Message.PLOT_PERMISSION_ITEM_DROP),
    ENTITY_INTERACT("entity_interact", Category.ENTITY, Message.PLOT_MENU_FLAG_ENTITY),
    TRADE("trade", Category.ENTITY, Message.PLOT_PERMISSION_TRADE),
    RIDE("ride", Category.ENTITY, Message.PLOT_PERMISSION_RIDE),
    ANIMAL("animal", Category.ENTITY, Message.PLOT_PERMISSION_ANIMAL),
    ENTITY_DAMAGE("entity_damage", Category.ENTITY, Message.PLOT_PERMISSION_ENTITY_DAMAGE),
    DECORATION("decoration", Category.ENTITY, Message.PLOT_PERMISSION_DECORATION),
    MANAGE_SETTINGS("manage_settings", Category.MANAGEMENT, Message.PLOT_PERMISSION_MANAGE_SETTINGS),
    MANAGE_AREA("manage_area", Category.MANAGEMENT, Message.PLOT_PERMISSION_MANAGE_AREA),
    CREATE_SUBPLOT("create_subplot", Category.MANAGEMENT, Message.PLOT_PERMISSION_CREATE_SUBPLOT),
    MANAGE_MEMBERS("manage_members", Category.MANAGEMENT, Message.PLOT_PERMISSION_MANAGE_MEMBERS),
    MANAGE_PERMISSIONS("manage_permissions", Category.MANAGEMENT, Message.PLOT_PERMISSION_MANAGE_PERMISSIONS),
    TRANSFER("transfer", Category.MANAGEMENT, Message.PLOT_PERMISSION_TRANSFER),
    DELETE("delete", Category.MANAGEMENT, Message.PLOT_PERMISSION_DELETE);

    private static final Map<String, PlotPermission> BY_KEY = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(PlotPermission::key, action -> action));
    private static final Map<Category, List<PlotPermission>> BY_CATEGORY = Arrays.stream(Category.values())
            .collect(Collectors.toUnmodifiableMap(category -> category,
                    category -> Arrays.stream(values()).filter(action -> action.category == category).toList()));

    public enum Category {
        BUILD(Message.PLOT_PERMISSION_CATEGORY_BUILD),
        INTERACTION(Message.PLOT_PERMISSION_CATEGORY_INTERACTION),
        STORAGE(Message.PLOT_PERMISSION_CATEGORY_STORAGE),
        ENTITY(Message.PLOT_PERMISSION_CATEGORY_ENTITY),
        MANAGEMENT(Message.PLOT_PERMISSION_CATEGORY_MANAGEMENT);

        private final Message label;

        Category(Message label) { this.label = label; }

        Message label() { return label; }

        List<PlotPermission> permissions() {
            return BY_CATEGORY.get(this);
        }
    }

    private final String key;
    private final Category category;
    private final Message label;

    PlotPermission(String key, Category category, Message label) {
        this.key = key;
        this.category = category;
        this.label = label;
    }

    public String key() { return key; }

    Category category() { return category; }

    Message label() { return label; }

    boolean appliesTo(Plot plot) { return this != CREATE_SUBPLOT || !plot.subPlot(); }

    static PlotPermission fromKey(String key) {
        return key == null ? null : BY_KEY.get(key);
    }
}
