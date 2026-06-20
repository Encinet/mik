package org.encinet.mik.module.menu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;

public final class MenuDialogs {

    private MenuDialogs() {
    }

    public static void openUrlConfirm(Player player, String label, String url) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("打开" + label, MenuItems.TITLE_COLOR))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(
                                Component.text()
                                        .append(Component.text("由于 Minecraft 的限制，点击确认后客户端还会再弹出一次打开链接确认", NamedTextColor.GRAY))
                                        .append(Component.newline())
                                        .append(Component.text("你确实要打开 ", NamedTextColor.GRAY))
                                        .append(Component.text(label, NamedTextColor.YELLOW))
                                        .append(Component.text(" 吗", NamedTextColor.GRAY))
                                        .append(Component.newline())
                                        .append(Component.text(url, NamedTextColor.AQUA))
                                        .build(), 280)))
                        .inputs(List.of())
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(
                                Component.text("确认打开", NamedTextColor.GREEN),
                                Component.text(url, NamedTextColor.GRAY),
                                90,
                                DialogAction.staticAction(ClickEvent.openUrl(url))),
                        ActionButton.create(
                                Component.text("返回主菜单", NamedTextColor.GRAY),
                                Component.text("回到主菜单", NamedTextColor.GRAY),
                                100,
                                DialogAction.staticAction(ClickEvent.runCommand("/menu")))
                )));
        player.showDialog(dialog);
    }
}
