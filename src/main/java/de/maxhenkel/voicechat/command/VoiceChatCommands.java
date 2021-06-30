package de.maxhenkel.voicechat.command;

import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.net.NetManager;
import de.maxhenkel.voicechat.net.SetGroupPacket;
import de.maxhenkel.voicechat.voice.common.PingPacket;
import de.maxhenkel.voicechat.voice.common.PlayerState;
import de.maxhenkel.voicechat.voice.server.ClientConnection;
import de.maxhenkel.voicechat.voice.server.PingManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

public class VoiceChatCommands implements CommandExecutor {

    public static Permission CONNECT_PERMISSION = new Permission("voicechat.connect", PermissionDefault.TRUE);
    public static Permission SPEAK_PERMISSION = new Permission("voicechat.speak", PermissionDefault.TRUE);
    public static Permission GROUPS_PERMISSION = new Permission("voicechat.groups", PermissionDefault.TRUE);
    public static Permission ADMIN_PERMISSION = new Permission("voicechat.admin", PermissionDefault.OP);

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String label, String[] args) {
        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("test")) {
                if (commandSender.hasPermission(ADMIN_PERMISSION)) {
                    return testCommand(commandSender, command, label, args);
                }
            } else if (args[0].equalsIgnoreCase("invite")) {
                if (commandSender.hasPermission(GROUPS_PERMISSION)) {
                    return inviteCommand(commandSender, command, label, args);
                }
            } else if (args[0].equalsIgnoreCase("join")) {
                if (commandSender.hasPermission(GROUPS_PERMISSION)) {
                    return joinCommand(commandSender, command, label, args);
                }
            }
        }
        return false;
    }

    private boolean testCommand(CommandSender commandSender, Command command, String label, String[] args) {
        if (args.length < 2) {
            return false;
        }

        Player player = commandSender.getServer().getPlayer(args[1]);

        if (player == null) {
            commandSender.sendMessage("Player not found");
            return true;
        }

        ClientConnection clientConnection = Voicechat.SERVER.getServer().getConnections().get(player.getUniqueId());

        if (clientConnection == null) {
            commandSender.sendMessage("Client not connected to voice chat");
            return true;
        }

        try {
            commandSender.sendMessage("Sending packet...");
            long timestamp = System.currentTimeMillis();
            Voicechat.SERVER.getServer().getPingManager().sendPing(clientConnection, 5000, new PingManager.PingListener() {
                @Override
                public void onPong(PingPacket packet) {
                    commandSender.sendMessage("Received packet in " + (System.currentTimeMillis() - timestamp) + "ms");
                }

                @Override
                public void onTimeout() {
                    commandSender.sendMessage("Request timed out");
                }
            });
            commandSender.sendMessage("Packet sent. Waiting for response...");
        } catch (Exception e) {
            commandSender.sendMessage("Failed to send packet: " + e.getMessage());
            e.printStackTrace();
        }
        return true;
    }

    private boolean inviteCommand(CommandSender commandSender, Command command, String label, String[] args) {
        if (!(commandSender instanceof Player)) {
            return false;
        }

        if (args.length < 2) {
            return false;
        }

        Player player = (Player) commandSender;

        Player otherPlayer = commandSender.getServer().getPlayer(args[1]);

        if (otherPlayer == null) {
            commandSender.sendMessage("Player not found");
            return true;
        }

        PlayerState state = Voicechat.SERVER.getServer().getPlayerStateManager().getState(player.getUniqueId());
        PlayerState otherState = Voicechat.SERVER.getServer().getPlayerStateManager().getState(otherPlayer.getUniqueId());

        if (otherState == null) {
            commandSender.sendMessage("Player not connected to voice chat");
            return true;
        }
        if (state == null) {
            commandSender.sendMessage("You are not connected to the voice chat");
        }
        if (!state.hasGroup()) {
            NetManager.sendMessage(player, Component.translatable("message.voicechat.not_in_group"));
            return true;
        }
        NetManager.sendMessage(otherPlayer, Component.translatable("message.voicechat.invite",
                Component.text(player.getName()),
                Component.text(state.getGroup()).toBuilder().color(NamedTextColor.GRAY).asComponent(),
                Component.text("[").toBuilder().append(
                        Component.translatable("message.voicechat.accept_invite")
                                .toBuilder()
                                .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/voicechat join \"" + state.getGroup() + "\""))
                                .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.voicechat.accept_invite.hover")))
                                .color(NamedTextColor.GREEN)
                                .build()
                ).append(Component.text("]")).color(NamedTextColor.GREEN).asComponent()
        ));
        NetManager.sendMessage(player, Component.translatable("message.voicechat.invite_successful", Component.text(otherPlayer.getName())));

        return true;
    }

    private boolean joinCommand(CommandSender commandSender, Command command, String label, String[] args) {
        if (!(commandSender instanceof Player)) {
            return false;
        }

        if (args.length < 2) {
            return false;
        }

        Player player = (Player) commandSender;
        PlayerState state = Voicechat.SERVER.getServer().getPlayerStateManager().getState(player.getUniqueId());

        if (state == null) {
            commandSender.sendMessage("You are not connected to the voice chat");
            return true;
        }

        if (!Voicechat.SERVER_CONFIG.groupsEnabled.get()) {
            NetManager.sendMessage(player, Component.translatable("message.voicechat.groups_disabled"));
            return true;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            sb.append(args[i]);
            sb.append(" ");
        }
        String groupName = sb.toString().trim();
        if (groupName.startsWith("\"")) {
            String[] split = groupName.split("\"");
            if (split.length > 1) {
                groupName = split[1];
            }
        }

        if (groupName.length() > 16) {
            NetManager.sendMessage(player, Component.translatable("message.voicechat.group_name_too_long"));
            return true;
        }

        if (!Voicechat.GROUP_REGEX.matcher(groupName).matches()) {
            NetManager.sendMessage(player, Component.translatable("message.voicechat.invalid_group_name"));
            return true;
        }

        NetManager.sendToClient(player, new SetGroupPacket(groupName));
        NetManager.sendMessage(player, Component.translatable("message.voicechat.join_successful", Component.text(groupName).toBuilder().color(NamedTextColor.GREEN).asComponent()));
        return true;
    }
}