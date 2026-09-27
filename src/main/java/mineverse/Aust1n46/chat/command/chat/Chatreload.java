package mineverse.Aust1n46.chat.command.chat;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import mineverse.Aust1n46.chat.MineverseChat;
import mineverse.Aust1n46.chat.api.MineverseChatAPI;
import mineverse.Aust1n46.chat.api.MineverseChatPlayer;
import mineverse.Aust1n46.chat.database.PlayerData;
import mineverse.Aust1n46.chat.localization.LocalizedMessage;
import mineverse.Aust1n46.chat.utilities.ChatFilterUtil;
import mineverse.Aust1n46.chat.utilities.ConfigMigrator;
import mineverse.Aust1n46.chat.utilities.Format;

public class Chatreload extends Command {
	private MineverseChat plugin = MineverseChat.getInstance();

	public Chatreload() {
		super("chatreload");
	}

	@Override
	public boolean execute(CommandSender sender, String command, String[] args) {
		if (!sender.hasPermission("venturechat.reload")) {
			sender.sendMessage(LocalizedMessage.COMMAND_NO_PERMISSION.toString());
			return true;
		}

		List<String> flags = args == null ? List.of() : Arrays.asList(args);
		boolean clearOffenses = flags.contains("--clear-offenses") || flags.contains("--reset");
		// Default is now the light path (config-only, no player data cycling)
		// because that is what admins want 99% of the time and it avoids the
		// disk-I/O hitch on the main thread. Use --full to also save/reload
		// every player's file — needed only after manually editing player data.
		boolean full = flags.contains("--full");

		long startNs = System.nanoTime();

		if (full) {
			PlayerData.savePlayerData();
			MineverseChatAPI.clearMineverseChatPlayerMap();
			MineverseChatAPI.clearNameMap();
			MineverseChatAPI.clearOnlineMineverseChatPlayerMap();
		}

		// Rewrite the reference example config so admins see the latest
		// commented defaults after every reload, not only at server start.
		try {
			plugin.saveResource("example_config_always_up_to_date!.yml", true);
		} catch (Exception ignored) {
		}

		// Merge any new default keys that shipped with a plugin update into
		// the admin's on-disk config.yml without overwriting their values.
		File configFile = new File(plugin.getDataFolder(), "config.yml");
		int added = 0;
		if (configFile.exists()) {
			added = ConfigMigrator.migrate(plugin, "config.yml", configFile);
		}

		plugin.reloadConfig();
		MineverseChat.initializeConfigReaders();

		if (full) {
			PlayerData.loadLegacyPlayerData();
			PlayerData.loadPlayerData();
			for (Player p : plugin.getServer().getOnlinePlayers()) {
				MineverseChatPlayer mcp = MineverseChatAPI.getMineverseChatPlayer(p);
				if (mcp == null) {
					Bukkit.getConsoleSender()
							.sendMessage(Format.FormatStringAll("&8[&eVentureChat&8]&c - Could not find player data post reload for currently online player: " + p.getName()));
					Bukkit.getConsoleSender().sendMessage(Format.FormatStringAll("&8[&eVentureChat&8]&c - There could be an issue with your player data saving."));
					String name = p.getName();
					UUID uuid = p.getUniqueId();
					mcp = new MineverseChatPlayer(uuid, name);
				}
				mcp.setOnline(true);
				mcp.setHasPlayed(false);
				mcp.setJsonFormat();
				MineverseChatAPI.addMineverseChatOnlinePlayerToMap(mcp);
				MineverseChatAPI.addNameToMap(mcp);
			}
		} else {
			// Light path: keep existing MineverseChatPlayer instances but refresh
			// per-player references that point at freshly re-initialized objects
			// (channels + JSON format group) — otherwise players could keep talking
			// through stale ChatChannel instances that no longer live in the registry.
			for (MineverseChatPlayer mcp : MineverseChatAPI.getOnlineMineverseChatPlayers()) {
				mcp.setJsonFormat();
				mineverse.Aust1n46.chat.channel.ChatChannel current = mcp.getCurrentChannel();
				if (current != null) {
					mineverse.Aust1n46.chat.channel.ChatChannel refreshed =
							mineverse.Aust1n46.chat.channel.ChatChannel.getChannel(current.getName());
					if (refreshed != null && refreshed != current) {
						mcp.setCurrentChannel(refreshed);
					}
				}
			}
		}

		if (clearOffenses) {
			for (Player p : plugin.getServer().getOnlinePlayers()) {
				ChatFilterUtil.forget(p.getUniqueId());
			}
		}

		long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
		StringBuilder note = new StringBuilder();
		note.append(" &7(").append(elapsedMs).append(" ms");
		note.append(full ? ", full" : ", light");
		if (added > 0) {
			note.append(", added ").append(added).append(" default key").append(added == 1 ? "" : "s");
		}
		if (clearOffenses) {
			note.append(", cleared offense counters");
		}
		note.append(')');
		Bukkit.getConsoleSender().sendMessage(Format.FormatStringAll("&8[&eVentureChat&8]&e - Config reloaded" + note));
		for (MineverseChatPlayer player : MineverseChatAPI.getOnlineMineverseChatPlayers()) {
			if (player.getPlayer().hasPermission("venturechat.reload")) {
				player.getPlayer().sendMessage(LocalizedMessage.CONFIG_RELOADED.toString());
			}
		}
		return true;
	}
}
