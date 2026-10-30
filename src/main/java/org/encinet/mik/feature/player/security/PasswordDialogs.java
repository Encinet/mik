package org.encinet.mik.feature.player.security;
import io.papermc.paper.connection.PlayerCommonConnection;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/** Sensitive input bypasses chat, command templates, menu drafts and callback payload logging. */
final class PasswordDialogs implements AutoCloseable, PasswordLogin.Prompt {
    private record Prompt(Key submit, Key cancel, long deadline, boolean game,
                          Consumer<DialogResponseView> accepted, Runnable cancelled) { }
    private final JavaPlugin plugin;
    private final LanguageService language;
    private final ConcurrentMap<PlayerCommonConnection, Prompt> prompts = new ConcurrentHashMap<>();

    PasswordDialogs(JavaPlugin plugin, LanguageService language) { this.plugin = plugin; this.language = language; }

    @Override public java.util.concurrent.CompletableFuture<String> login(PlayerConfigurationConnection connection, Language locale) {
        var response = new java.util.concurrent.CompletableFuture<String>();
        show(connection, locale, Message.PASSWORD_LOGIN, text(locale, Message.PASSWORD_LOGIN_HINT),
                List.of(DialogInput.text("password", text(locale, Message.PASSWORD_CURRENT)).maxLength(256).build()),
                Message.PASSWORD_LOGIN, value -> response.complete(value.getText("password")), () -> response.cancel(false));
        return response;
    }

    void show(PlayerCommonConnection connection, Language locale, Message title, Component body,
              List<DialogInput> inputs, Message submit, Consumer<DialogResponseView> accepted, Runnable cancelled) {
        String nonce = UUID.randomUUID().toString();
        Prompt prompt = new Prompt(Key.key("mik", "password/" + nonce + "/submit"),
                Key.key("mik", "password/" + nonce + "/cancel"), System.nanoTime() + 120_000_000_000L,
                connection instanceof PlayerGameConnection, accepted, cancelled);
        Prompt previous = prompts.put(connection, prompt);
        if (previous != null) previous.cancelled().run();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(text(locale, title)).canCloseWithEscape(true).pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(body, 360))).inputs(inputs).build())
                .type(DialogType.confirmation(
                        ActionButton.create(text(locale, submit), null, 120, DialogAction.customClick(prompt.submit(), null)),
                        ActionButton.create(text(locale, Message.CLOSE), null, 100, DialogAction.customClick(prompt.cancel(), null)))));
        try {
            if (connection instanceof PlayerConfigurationConnection config) config.getAudience().showDialog(dialog);
            else if (connection instanceof PlayerGameConnection game) game.getPlayer().showDialog(dialog);
            else throw new IllegalStateException("Unsupported password connection");
        } catch (RuntimeException error) {
            prompts.remove(connection, prompt);
            cancelled.run();
            throw error;
        }
    }

    void click(PlayerCustomClickEvent event) {
        PlayerCommonConnection connection = event.getCommonConnection();
        Prompt prompt = prompts.get(connection);
        if (prompt == null || !(event.getIdentifier().equals(prompt.submit()) || event.getIdentifier().equals(prompt.cancel()))) return;
        if (!prompts.remove(connection, prompt)) return;
        Runnable action = () -> {
            if (System.nanoTime() >= prompt.deadline() || !connection.isConnected()
                    || event.getIdentifier().equals(prompt.cancel()) || event.getDialogResponseView() == null) {
                prompt.cancelled().run();
            } else prompt.accepted().accept(event.getDialogResponseView());
        };
        if (prompt.game()) plugin.getServer().getScheduler().runTask(plugin, action);
        else action.run();
    }

    void expire() {
        prompts.forEach((connection, prompt) -> {
            if ((!connection.isConnected() || System.nanoTime() >= prompt.deadline()) && prompts.remove(connection, prompt)) {
                prompt.cancelled().run();
            }
        });
    }

    @Override public void cancel(PlayerCommonConnection connection) {
        Prompt prompt = prompts.remove(connection);
        if (prompt != null) prompt.cancelled().run();
    }

    private Component text(Language locale, Message message) {
        return Component.text(language.t(locale, message), NamedTextColor.WHITE);
    }

    @Override public void close() {
        prompts.forEach((connection, prompt) -> {
            if (prompts.remove(connection, prompt)) prompt.cancelled().run();
        });
    }
}
