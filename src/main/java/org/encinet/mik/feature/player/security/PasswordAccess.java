package org.encinet.mik.feature.player.security;
import org.bukkit.entity.Player;
import java.util.UUID;

/** Feature-owned entry points used by governance and cross-feature menus. */
public interface PasswordAccess extends PasswordAccounts {
    void open(Player player, Runnable back);
    void reset(Player actor, UUID target);
    void manage(Player actor, UUID target, Runnable back);
    void roleChanged(UUID playerId);
}
