package fr.xephi.authme.process.register;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import fr.xephi.authme.AuthMe;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.cache.limbo.LimboCache;
import fr.xephi.authme.output.MessageKey;
import fr.xephi.authme.permission.AuthGroupType;
import fr.xephi.authme.process.ProcessService;
import fr.xephi.authme.process.SynchronousProcess;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.EmailSettings;
import fr.xephi.authme.settings.properties.HooksSettings;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.SecuritySettings;
import fr.xephi.authme.task.PlayerDataTaskManager;
import fr.xephi.authme.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.inject.Inject;

/**
 */
public class ProcessSyncPasswordRegister implements SynchronousProcess {

    @Inject
    private AuthMe plugin;

    @Inject
    private ProcessService service;

    @Inject
    private LimboCache limboCache;

    @Inject
    private PlayerDataTaskManager playerDataTaskManager;

    ProcessSyncPasswordRegister() {
    }


    private void sendBungeeMessage(Player player) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Forward");
        out.writeUTF("ALL");
        out.writeUTF("AuthMe");
        out.writeUTF("register;" + player.getName());
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }

    private void forceCommands(Player player) {
        for (String command : service.getProperty(RegistrationSettings.FORCE_REGISTER_COMMANDS)) {
            player.performCommand(command.replace("%p", player.getName()));
        }
        for (String command : service.getProperty(RegistrationSettings.FORCE_REGISTER_COMMANDS_AS_CONSOLE)) {
            Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(),
                command.replace("%p", player.getName()));
        }
    }

    /**
     * Request that the player log in.
     *
     * @param player the player
     */
    private void requestLogin(Player player) {
        final String name = player.getName().toLowerCase();
        limboCache.updatePlayerData(player);
        playerDataTaskManager.registerTimeoutTask(player);
        playerDataTaskManager.registerMessageTask(name, true);

        if (player.isInsideVehicle() && player.getVehicle() != null) {
            player.getVehicle().eject();
        }
    }

    public void processPasswordRegister(Player player) {
        if (!Settings.getRegisteredGroup.isEmpty()) {
            service.setGroup(player, AuthGroupType.REGISTERED);
        }

        service.send(player, MessageKey.REGISTER_SUCCESS);

        if (!service.getProperty(EmailSettings.MAIL_ACCOUNT).isEmpty()) {
            service.send(player, MessageKey.ADD_EMAIL_MESSAGE);
        }

        player.saveData();

        if (!service.getProperty(SecuritySettings.REMOVE_SPAM_FROM_CONSOLE)) {
            ConsoleLogger.info(player.getName() + " registered " + Utils.getPlayerIp(player));
        }

        // Kick Player after Registration is enabled, kick the player
        if (service.getProperty(RegistrationSettings.FORCE_KICK_AFTER_REGISTER)) {
            player.kickPlayer(service.retrieveSingleMessage(MessageKey.REGISTER_SUCCESS));
            return;
        }

        // Register is now finished; we can force all commands
        forceCommands(player);

        // Request login after registration
        if (service.getProperty(RegistrationSettings.FORCE_LOGIN_AFTER_REGISTER)) {
            requestLogin(player);
            return;
        }

        if (service.getProperty(HooksSettings.BUNGEECORD)) {
            sendBungeeMessage(player);
        }

        sendTo(player);
    }

    private void sendTo(Player player) {
        if (!service.getProperty(HooksSettings.BUNGEECORD_SERVER).isEmpty()) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(service.getProperty(HooksSettings.BUNGEECORD_SERVER));
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        }
    }
}
