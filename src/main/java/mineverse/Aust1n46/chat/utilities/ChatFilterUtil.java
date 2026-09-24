package mineverse.Aust1n46.chat.utilities;

import static mineverse.Aust1n46.chat.MineverseChat.getInstance;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import mineverse.Aust1n46.chat.MineverseChat;
import mineverse.Aust1n46.chat.api.MineverseChatAPI;
import mineverse.Aust1n46.chat.api.MineverseChatPlayer;
import mineverse.Aust1n46.chat.channel.ChatChannel;

/**
 * Runtime checks that block a chat message before it is dispatched:
 * <ul>
 *   <li><b>Anti-flood</b> — same message repeated too quickly, long runs of
 *       the same character, or an oversized amount of uppercase letters.</li>
 *   <li><b>Blocked words</b> — plain word list that cancels the message
 *       outright, unlike the regex {@code filters} list which only rewrites
 *       parts of the message.</li>
 * </ul>
 *
 * <p>All state is kept off the {@link MineverseChatPlayer} model to avoid
 * touching persisted player data — it is per-session only.</p>
 */
public final class ChatFilterUtil {

	public enum CheckResult {
		OK,
		BLOCKED_WORD,
		DUPLICATE,
		CHAR_REPEAT,
		CAPS,
		SPAM,
		GIBBERISH
	}

	private static final class LastMessage {
		final String stripped;
		final long timeMillis;

		LastMessage(String stripped, long timeMillis) {
			this.stripped = stripped;
			this.timeMillis = timeMillis;
		}
	}

	private static final ConcurrentHashMap<UUID, LastMessage> LAST_MESSAGES = new ConcurrentHashMap<>();

	private static final class OffenseInfo {
		int count;
		long lastTimeMillis;

		OffenseInfo(int count, long lastTimeMillis) {
			this.count = count;
			this.lastTimeMillis = lastTimeMillis;
		}
	}

	// Per player, per rule: how many times they have triggered that rule
	// within the rule's recidivism window.
	private static final ConcurrentHashMap<UUID, EnumMap<CheckResult, OffenseInfo>> OFFENSES = new ConcurrentHashMap<>();

	private ChatFilterUtil() {
	}

	/**
	 * Runs blocked-word and anti-flood checks against a raw chat message.
	 * The returned value tells the caller whether to drop the message and
	 * which rule fired (so a specific error can be shown to the sender).
	 */
	public static CheckResult check(MineverseChatPlayer sender, String rawMessage) {
		if (sender == null || sender.getPlayer() == null || rawMessage == null) {
			return CheckResult.OK;
		}
		final Player player = sender.getPlayer();
		final FileConfiguration cfg = getInstance().getConfig();
		final String stripped = ChatColor.stripColor(rawMessage).trim();

		if (cfg.getBoolean("blockedwords.enabled", false)
				&& !hasBypass(player, cfg.getString("blockedwords.bypass_permission", "None"))) {
			List<String> words = cfg.getStringList("blockedwords.words");
			if (words != null && !words.isEmpty()) {
				String haystack = stripped.toLowerCase(Locale.ROOT);
				for (String word : words) {
					if (word == null || word.isEmpty()) {
						continue;
					}
					if (haystack.contains(word.toLowerCase(Locale.ROOT))) {
						return CheckResult.BLOCKED_WORD;
					}
				}
			}
		}

		if (!cfg.getBoolean("antiflood.enabled", false)) {
			return CheckResult.OK;
		}

		if (cfg.getBoolean("antiflood.duplicate_messages.enabled", true)
				&& !hasBypass(player, cfg.getString("antiflood.duplicate_messages.bypass_permission", "None"))) {
			long windowSeconds = Math.max(cfg.getLong("antiflood.duplicate_messages.window_seconds", 15L), 0L);
			int minLength = Math.max(cfg.getInt("antiflood.duplicate_messages.min_length", 3), 1);
			if (stripped.length() >= minLength) {
				LastMessage previous = LAST_MESSAGES.get(player.getUniqueId());
				if (previous != null && previous.stripped.equalsIgnoreCase(stripped)
						&& (System.currentTimeMillis() - previous.timeMillis) < windowSeconds * 1000L) {
					return CheckResult.DUPLICATE;
				}
			}
		}

		if (cfg.getBoolean("antiflood.character_repeat.enabled", true)
				&& !hasBypass(player, cfg.getString("antiflood.character_repeat.bypass_permission", "None"))) {
			int maxRepeat = Math.max(cfg.getInt("antiflood.character_repeat.max_repeat", 6), 2);
			if (hasCharRepeat(stripped, maxRepeat)) {
				return CheckResult.CHAR_REPEAT;
			}
		}

		if (cfg.getBoolean("antiflood.caps.enabled", true)
				&& !hasBypass(player, cfg.getString("antiflood.caps.bypass_permission", "None"))) {
			int minLength = Math.max(cfg.getInt("antiflood.caps.min_length", 8), 1);
			int maxPercent = Math.min(Math.max(cfg.getInt("antiflood.caps.max_caps_percent", 60), 0), 100);
			if (hasTooMuchCaps(stripped, minLength, maxPercent)) {
				return CheckResult.CAPS;
			}
		}

		if (cfg.getBoolean("antiflood.gibberish.enabled", true)
				&& !hasBypass(player, cfg.getString("antiflood.gibberish.bypass_permission", "None"))) {
			int minLength = Math.max(cfg.getInt("antiflood.gibberish.min_length", 8), 3);
			int maxConsonantsInRow = Math.max(cfg.getInt("antiflood.gibberish.max_consonants_in_row", 5), 2);
			int maxWordLength = Math.max(cfg.getInt("antiflood.gibberish.max_word_length", 25), 5);
			int minVowelPercent = Math.min(Math.max(cfg.getInt("antiflood.gibberish.min_vowel_ratio_percent", 15), 0), 100);
			if (isGibberish(stripped, minLength, maxConsonantsInRow, maxWordLength, minVowelPercent)) {
				return CheckResult.GIBBERISH;
			}
		}

		return CheckResult.OK;
	}

	// Latin vowels including French accents plus the ligatures used in
	// modern French. Y is treated as a vowel because it very often is one in
	// French words ("cyprès", "psychologie"), which keeps false positives low.
	private static final String VOWELS = "aeiouyàáâãäåèéêëìíîïòóôõöùúûüýÿœæ";

	private static boolean isGibberish(String message, int minLength, int maxConsonantsInRow, int maxWordLength,
			int minVowelPercent) {
		if (message == null || message.isEmpty()) {
			return false;
		}
		for (String token : message.split("\\s+")) {
			if (token.isEmpty()) {
				continue;
			}
			// Skip URLs, emails and technical identifiers so we do not
			// misfire on things like "https://site.com" or "my_var_name".
			if (token.indexOf('.') >= 0 || token.indexOf(':') >= 0 || token.indexOf('/') >= 0
					|| token.indexOf('@') >= 0 || token.indexOf('_') >= 0) {
				continue;
			}
			// Keep only letters. Skips digits, punctuation, glyphs.
			StringBuilder letters = new StringBuilder(token.length());
			for (int i = 0; i < token.length(); ) {
				int cp = token.codePointAt(i);
				if (Character.isLetter(cp)) {
					letters.appendCodePoint(Character.toLowerCase(cp));
				}
				i += Character.charCount(cp);
			}
			if (letters.length() == 0) {
				continue;
			}
			String word = letters.toString();
			if (word.length() > maxWordLength) {
				return true;
			}
			if (word.length() < minLength) {
				continue;
			}
			int consonantRun = 0;
			int vowels = 0;
			for (int i = 0; i < word.length(); i++) {
				char c = word.charAt(i);
				if (VOWELS.indexOf(c) >= 0) {
					vowels++;
					consonantRun = 0;
				} else {
					consonantRun++;
					if (consonantRun > maxConsonantsInRow) {
						return true;
					}
				}
			}
			int ratio = (vowels * 100) / word.length();
			if (ratio < minVowelPercent) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Remembers the message as the sender's most recent one so the duplicate
	 * check can compare against it on the next call. Only invoke this AFTER
	 * the message has passed every check.
	 */
	public static void recordMessage(MineverseChatPlayer sender, String rawMessage) {
		if (sender == null || sender.getPlayer() == null || rawMessage == null) {
			return;
		}
		String stripped = ChatColor.stripColor(rawMessage).trim();
		LAST_MESSAGES.put(sender.getPlayer().getUniqueId(),
				new LastMessage(stripped, System.currentTimeMillis()));
	}

	/**
	 * Removes the sender's cached last message. Call on quit to keep the
	 * in-memory map tidy.
	 */
	public static void forget(UUID uuid) {
		if (uuid != null) {
			LAST_MESSAGES.remove(uuid);
			OFFENSES.remove(uuid);
		}
	}

	/**
	 * Executes the configured action list for a rule that just triggered.
	 * Returns {@code true} if the caller should cancel the message (i.e. at
	 * least one {@code block} action was configured — the default).
	 *
	 * <p>Each action is a plain string parsed on the fly. Supported forms:
	 * <ul>
	 *   <li>{@code block} — cancel the message.</li>
	 *   <li>{@code warn} — send the rule's {@code message} to the sender.</li>
	 *   <li>{@code notify_staff} — broadcast the rule's {@code staff_alert_format}
	 *       to every online player that holds the rule's {@code staff_permission}.</li>
	 *   <li>{@code mute:<duration>} — mute the sender in {@code channel}. Duration
	 *       accepts the same units as elsewhere (e.g. {@code mute:5m}, {@code mute:1h}).
	 *       Use {@code mute:0} for a permanent mute.</li>
	 *   <li>{@code kick:<reason>} — kick the sender. Reason accepts {@code &} colours.</li>
	 *   <li>{@code cmd:<command>} — run a command from the console. Placeholders
	 *       {@code {player}}, {@code {rule}}, {@code {message}}, {@code {channel}}
	 *       are substituted.</li>
	 * </ul>
	 */
	public static boolean executeActions(MineverseChatPlayer sender, ChatChannel channel, CheckResult rule,
			String rawMessage) {
		if (rule == null || rule == CheckResult.OK || sender == null || sender.getPlayer() == null) {
			return false;
		}
		Player player = sender.getPlayer();
		FileConfiguration cfg = getInstance().getConfig();
		String base = configBaseFor(rule);
		if (base == null) {
			return false;
		}

		// Recidivism: track how many times this player has tripped this rule
		// recently, then pick the highest matching level's action list.
		long resetMinutes = Math.max(cfg.getLong(base + ".recidivism.reset_after_minutes", 0L), 0L);
		int offenseCount = recordAndGetOffenseCount(player.getUniqueId(), rule, resetMinutes * 60_000L);
		List<String> actions = resolveActionsForOffenseCount(cfg, base, offenseCount);

		boolean shouldBlock = false;
		for (String rawAction : actions) {
			if (rawAction == null || rawAction.isEmpty()) {
				continue;
			}
			String action = rawAction.trim();
			String argument;
			int colon = action.indexOf(':');
			String verb;
			if (colon > 0) {
				verb = action.substring(0, colon).trim().toLowerCase(Locale.ROOT);
				argument = action.substring(colon + 1).trim();
			} else {
				verb = action.toLowerCase(Locale.ROOT);
				argument = "";
			}
			try {
				switch (verb) {
					case "block":
						shouldBlock = true;
						break;
					case "warn":
						String warnRaw = cfg.getString(base + ".message", "");
						if (warnRaw != null && !warnRaw.isEmpty()) {
							String warnMessage = Format.FormatStringAll(
									substitutePlaceholders(warnRaw, sender, channel, rule, rawMessage, offenseCount));
							if (!warnMessage.isEmpty()) {
								player.sendMessage(warnMessage);
							}
						}
						break;
					case "notify_staff":
					case "notify-staff":
					case "notifystaff":
						notifyStaff(sender, channel, rule, rawMessage, base, offenseCount);
						break;
					case "mute":
						applyMute(sender, channel, argument, rule, base, rawMessage, offenseCount);
						break;
					case "kick":
						applyKick(sender, channel, player, rule, argument, base, rawMessage, offenseCount);
						break;
					case "cmd":
					case "command":
						runCommand(sender, channel, rule, rawMessage, argument, offenseCount);
						break;
					default:
						// Unknown action: log once so the admin notices the typo.
						Bukkit.getLogger().warning("[VentureChat] Unknown chat-filter action: '" + rawAction
								+ "' under " + base + ".actions");
				}
			} catch (Throwable t) {
				Bukkit.getLogger().warning("[VentureChat] Failed to run chat-filter action '" + rawAction
						+ "' for " + player.getName() + ": " + t.getMessage());
			}
		}
		return shouldBlock;
	}

	private static String configBaseFor(CheckResult rule) {
		switch (rule) {
			case BLOCKED_WORD:
				return "blockedwords";
			case DUPLICATE:
				return "antiflood.duplicate_messages";
			case CHAR_REPEAT:
				return "antiflood.character_repeat";
			case CAPS:
				return "antiflood.caps";
			case GIBBERISH:
				return "antiflood.gibberish";
			case SPAM:
				return "antispam";
			default:
				return null;
		}
	}

	private static void notifyStaff(MineverseChatPlayer sender, ChatChannel channel, CheckResult rule,
			String rawMessage, String base, int offenseCount) {
		FileConfiguration cfg = getInstance().getConfig();
		String permission = firstNonEmpty(
				cfg.getString(base + ".staff_permission"),
				cfg.getString("antiflood.staff_permission"),
				cfg.getString("chat_actions.staff_permission"),
				"venturechat.staff.notify");
		String template = firstNonEmpty(
				cfg.getString(base + ".staff_alert_format"),
				cfg.getString("antiflood.staff_alert_format"),
				cfg.getString("chat_actions.staff_alert_format"),
				"&8[&cChatFilter&8] &7{player} triggered &e{rule}&7 in &f{channel}&7: &f{message}");
		String rendered = Format.FormatStringAll(
				substitutePlaceholders(template, sender, channel, rule, rawMessage, offenseCount));
		if (rendered.isEmpty()) {
			return;
		}
		Bukkit.getConsoleSender().sendMessage(rendered);
		if (permission == null || permission.isEmpty() || permission.equalsIgnoreCase("None")) {
			return;
		}
		for (MineverseChatPlayer online : MineverseChatAPI.getOnlineMineverseChatPlayers()) {
			Player p = online.getPlayer();
			if (p != null && p.hasPermission(permission)) {
				p.sendMessage(rendered);
			}
		}
	}

	private static void applyMute(MineverseChatPlayer sender, ChatChannel channel, String argument, CheckResult rule,
			String base, String rawMessage, int offenseCount) {
		if (channel == null) {
			return;
		}
		String reason = resolveMuteReason(sender, channel, rule, rawMessage, base, offenseCount);
		if (argument == null || argument.isEmpty() || argument.equals("0")) {
			sender.addMute(channel.getName(), reason);
			return;
		}
		long durationMillis = Format.parseTimeStringToMillis(argument);
		if (durationMillis <= 0) {
			sender.addMute(channel.getName(), reason);
			return;
		}
		sender.addMute(channel.getName(), System.currentTimeMillis() + durationMillis, reason);
	}

	private static String resolveMuteReason(MineverseChatPlayer sender, ChatChannel channel, CheckResult rule,
			String rawMessage, String base, int offenseCount) {
		FileConfiguration cfg = getInstance().getConfig();
		String template = firstNonEmpty(
				cfg.getString(base + ".mute_reason"),
				cfg.getString("chat_actions.default_mute_reason"),
				"Chat filter: {rule}");
		return substitutePlaceholders(template, sender, channel, rule, rawMessage, offenseCount);
	}

	private static void applyKick(MineverseChatPlayer sender, ChatChannel channel, Player player, CheckResult rule,
			String argument, String base, String rawMessage, int offenseCount) {
		FileConfiguration cfg = getInstance().getConfig();
		String template = firstNonEmpty(
				argument,
				cfg.getString(base + ".kick_reason"),
				cfg.getString("chat_actions.default_kick_reason"),
				"&cKicked by chat filter");
		String rendered = Format.FormatStringAll(
				substitutePlaceholders(template, sender, channel, rule, rawMessage, offenseCount));
		SchedulerUtil.runForEntity(MineverseChat.getInstance(), player, () -> {
			try {
				player.kickPlayer(rendered);
			} catch (Throwable ignored) {
			}
		});
	}

	private static String firstNonEmpty(String... values) {
		for (String v : values) {
			if (v != null && !v.isEmpty()) {
				return v;
			}
		}
		return "";
	}

	private static void runCommand(MineverseChatPlayer sender, ChatChannel channel, CheckResult rule, String rawMessage,
			String commandTemplate, int offenseCount) {
		if (commandTemplate == null || commandTemplate.isEmpty()) {
			return;
		}
		String command = substitutePlaceholders(commandTemplate, sender, channel, rule, rawMessage, offenseCount);
		if (command.startsWith("/")) {
			command = command.substring(1);
		}
		final String toRun = command;
		SchedulerUtil.runGlobal(MineverseChat.getInstance(),
				() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), toRun));
	}

	private static String substitutePlaceholders(String template, MineverseChatPlayer sender, ChatChannel channel,
			CheckResult rule, String rawMessage, int offenseCount) {
		String player = sender == null ? "unknown" : sender.getName();
		String channelName = channel == null ? "unknown" : channel.getName();
		String message = rawMessage == null ? "" : rawMessage;
		String ruleName = rule == null ? "OK" : rule.name();
		return template
				.replace("{player}", player)
				.replace("{channel}", channelName)
				.replace("{rule}", ruleName)
				.replace("{message}", message)
				.replace("{offense_count}", String.valueOf(offenseCount));
	}

	private static int recordAndGetOffenseCount(UUID uuid, CheckResult rule, long resetAfterMillis) {
		EnumMap<CheckResult, OffenseInfo> perPlayer = OFFENSES.computeIfAbsent(uuid,
				k -> new EnumMap<>(CheckResult.class));
		synchronized (perPlayer) {
			long now = System.currentTimeMillis();
			OffenseInfo info = perPlayer.get(rule);
			if (info == null || (resetAfterMillis > 0L && now - info.lastTimeMillis > resetAfterMillis)) {
				info = new OffenseInfo(1, now);
				perPlayer.put(rule, info);
			} else {
				info.count++;
				info.lastTimeMillis = now;
			}
			return info.count;
		}
	}

	/**
	 * Picks the action list to run for a given offense count. The base
	 * {@code <base>.actions:} list is used at level 1; keys under
	 * {@code <base>.recidivism.levels.<n>.actions:} override it as the
	 * count reaches {@code <n>}. When several level keys match, the highest
	 * one wins so admins can define a 3-tier ladder like 2 / 5 / 10.
	 */
	private static List<String> resolveActionsForOffenseCount(FileConfiguration cfg, String base, int offenseCount) {
		List<String> base_actions = cfg.getStringList(base + ".actions");
		if (base_actions == null || base_actions.isEmpty()) {
			base_actions = cfg.getStringList("chat_actions.default_actions");
			if (base_actions == null || base_actions.isEmpty()) {
				base_actions = List.of("block", "warn");
			}
		}
		ConfigurationSection levels = cfg.getConfigurationSection(base + ".recidivism.levels");
		if (levels == null) {
			return base_actions;
		}
		int bestLevel = 0;
		for (String key : levels.getKeys(false)) {
			try {
				int lvl = Integer.parseInt(key);
				if (lvl <= offenseCount && lvl > bestLevel) {
					bestLevel = lvl;
				}
			} catch (NumberFormatException ignored) {
			}
		}
		if (bestLevel == 0) {
			return base_actions;
		}
		List<String> levelActions = cfg.getStringList(base + ".recidivism.levels." + bestLevel + ".actions");
		if (levelActions == null || levelActions.isEmpty()) {
			return base_actions;
		}
		return levelActions;
	}

	private static boolean hasBypass(Player player, String permission) {
		if (permission == null || permission.isEmpty() || permission.equalsIgnoreCase("None")) {
			return false;
		}
		return player.hasPermission(permission);
	}

	private static boolean hasCharRepeat(String message, int maxRepeat) {
		if (message == null || message.length() < maxRepeat) {
			return false;
		}
		int consecutive = 1;
		int previous = message.codePointAt(0);
		for (int i = Character.charCount(previous); i < message.length(); ) {
			int cp = message.codePointAt(i);
			if (cp == previous) {
				consecutive++;
				if (consecutive > maxRepeat) {
					return true;
				}
			} else {
				consecutive = 1;
				previous = cp;
			}
			i += Character.charCount(cp);
		}
		return false;
	}

	private static boolean hasTooMuchCaps(String message, int minLength, int maxPercent) {
		if (message == null) {
			return false;
		}
		int letters = 0;
		int upper = 0;
		for (int i = 0; i < message.length(); ) {
			int cp = message.codePointAt(i);
			if (Character.isLetter(cp)) {
				letters++;
				if (Character.isUpperCase(cp)) {
					upper++;
				}
			}
			i += Character.charCount(cp);
		}
		if (letters < minLength) {
			return false;
		}
		return (upper * 100 / letters) > maxPercent;
	}
}
