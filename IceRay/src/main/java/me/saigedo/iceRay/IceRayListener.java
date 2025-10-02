package me.saigedo.iceRay;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.event.PlayerSwingEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class IceRayListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSwing(final PlayerSwingEvent event) {
        final Player player = event.getPlayer();
        final IceRay iceRay = CoreAbility.getAbility(player, IceRay.class);

        if (iceRay == null) return;
        if (!BendingPlayer.getBendingPlayer(player).getBoundAbilityName().equalsIgnoreCase("FrostBreath")) return;

        if (iceRay.getAbilityState() == IceRay.AbilityState.PREPARED && player.isSneaking()) {
            iceRay.startAbility();
        }
    }
}
