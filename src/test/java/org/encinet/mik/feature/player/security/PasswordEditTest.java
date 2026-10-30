package org.encinet.mik.feature.player.security;

import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.PlayerRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PasswordEditTest {
    @TempDir Path directory;
    private static final String PASSWORD = "river8642!";

    @ParameterizedTest
    @EnumSource(value = PlayerRole.class, names = {"MODERATOR", "CUSTODIAN"})
    void staffRestrictionsMatchBeforeOpeningAndAfterSubmittingTheForm(PlayerRole role) {
        UUID id = UUID.randomUUID();
        try (var accounts = new PasswordService(directory.resolve("passwords.db"))) {
            accounts.open();
            var set = assertThrows(PasswordException.class, () -> accounts.set(id, "Alex", accounts.credential(id),
                    PASSWORD, PASSWORD, () -> role, null));
            assertEquals(Message.PASSWORD_REQUIRED_ROLE, PasswordFeedback.failure(set.code()));
            assertEquals(PasswordEdit.SET.restriction(false, role), PasswordFeedback.failure(set.code()));

            // The form was allowed before the player acquired a staff role; commit must give the same reason.
            assertNull(PasswordEdit.REMOVE.restriction(true, PlayerRole.MEMBER));
            var reset = accounts.reset(id, accounts.credential(id), null, null, () -> PlayerRole.CUSTODIAN, () -> role);
            var remove = assertThrows(PasswordException.class, () -> accounts.remove(id, reset.credential(),
                    reset.value(), () -> role, null));
            assertEquals(Message.PASSWORD_KEEP_ROLE, PasswordFeedback.failure(remove.code()));
            assertEquals(PasswordEdit.REMOVE.restriction(true, role), PasswordFeedback.failure(remove.code()));
            assertTrue(accounts.hasPassword(id));
            assertNull(PasswordEdit.CHANGE.restriction(true, role));
        }
    }

    @Test
    void existingAndMissingPasswordsExplainTheAvailableCommand() throws Exception {
        var bundle = FluentTestSupport.bundle(Language.ZH_CN);
        var alreadySet = PasswordEdit.SET.restriction(true, PlayerRole.MEMBER);
        assertEquals("已设置密码，请用 /password change 修改。", FluentTestSupport.text(bundle, alreadySet));
        for (var operation : new PasswordEdit[]{PasswordEdit.CHANGE, PasswordEdit.REMOVE}) {
            var missing = operation.restriction(false, PlayerRole.MEMBER);
            assertEquals("尚未设置密码，可用 /password set 设置。", FluentTestSupport.text(bundle, missing));
        }
    }

    @Test
    void removalExplainsTheConsequenceAndSuccessNamesTheCompletedAction() throws Exception {
        var bundle = FluentTestSupport.bundle(Language.ZH_CN);
        assertEquals("移除后，登录将不再验证密码。请输入当前密码确认。",
                FluentTestSupport.text(bundle, PasswordEdit.REMOVE.instructions));
        assertEquals("密码已设置。", FluentTestSupport.text(bundle, PasswordEdit.SET.completed));
        assertEquals("密码已修改。", FluentTestSupport.text(bundle, PasswordEdit.CHANGE.completed));
        assertEquals("密码已移除。", FluentTestSupport.text(bundle, PasswordEdit.REMOVE.completed));
    }
}
