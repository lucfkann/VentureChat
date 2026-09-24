package mineverse.Aust1n46.chat.utilities;

import static mineverse.Aust1n46.chat.MineverseChat.getInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mineverse.Aust1n46.chat.MineverseChat;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.json.simple.JSONObject;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import mineverse.Aust1n46.chat.ClickAction;
import mineverse.Aust1n46.chat.api.MineverseChatAPI;
import mineverse.Aust1n46.chat.api.MineverseChatPlayer;
import mineverse.Aust1n46.chat.json.JsonAttribute;
import mineverse.Aust1n46.chat.json.JsonFormat;
import mineverse.Aust1n46.chat.localization.LocalizedMessage;
import mineverse.Aust1n46.chat.versions.VersionHandler;

/**
 * Class containing chat formatting methods.
 */
public class Format {
	public static final int LEGACY_COLOR_CODE_LENGTH = 2;
	public static final int HEX_COLOR_CODE_LENGTH = 14;
	public static final String HEX_COLOR_CODE_PREFIX = "#";
	public static final char BUKKIT_COLOR_CODE_PREFIX_CHAR = '\u00A7';
	public static final String BUKKIT_COLOR_CODE_PREFIX = String.valueOf(BUKKIT_COLOR_CODE_PREFIX_CHAR);
	public static final String BUKKIT_HEX_COLOR_CODE_PREFIX = "x";
	public static final String DEFAULT_COLOR_CODE = BUKKIT_COLOR_CODE_PREFIX + "f";

	private static final Pattern LEGACY_CHAT_COLOR_DIGITS_PATTERN = Pattern.compile("&([0-9])");
	private static final Pattern LEGACY_CHAT_COLOR_PATTERN = Pattern.compile(
			"(?<!(&x(&[a-fA-F0-9]){5}))(?<!(&x(&[a-fA-F0-9]){4}))(?<!(&x(&[a-fA-F0-9]){3}))(?<!(&x(&[a-fA-F0-9]){2}))(?<!(&x(&[a-fA-F0-9]){1}))(?<!(&x))(&)([0-9a-fA-F])");
	
	private static final Pattern PLACEHOLDERAPI_PLACEHOLDER_PATTERN = Pattern.compile("\\{([^\\{\\}]+)\\}");
	private static final Pattern NEXO_PLACEHOLDER_PATTERN = Pattern.compile("%nexo_[^%]+%", Pattern.CASE_INSENSITIVE);
	
	public static final long MILLISECONDS_PER_DAY = 86400000;
	public static final long MILLISECONDS_PER_HOUR = 3600000;
	public static final long MILLISECONDS_PER_MINUTE = 60000;
	public static final long MILLISECONDS_PER_SECOND = 1000;
	
	public static final String DEFAULT_MESSAGE_SOUND = "ENTITY_PLAYER_LEVELUP";
	public static final String DEFAULT_LEGACY_MESSAGE_SOUND = "LEVEL_UP";

	/**
     * Converts a message to Minecraft JSON formatting while applying the
     * {@link JsonFormat} from the config.
     *
     * @param sender {@link MineverseChatPlayer} wrapper of the message sender.
     * @param format The format section of the message.
     * @param chat   The chat section of the message.
     * @return {@link String}
     */
	public static String convertToJson(MineverseChatPlayer sender, String format, String chat) {
		JsonFormat JSONformat = JsonFormat.getJsonFormat(sender.getJsonFormat());
		String c = escapeJsonChars(chat);
		String json = "[\"\",{\"text\":\"\",\"extra\":[";
		json += convertPlaceholders(format, JSONformat, sender);
		json += "]}";
		json += "," + convertItemPlaceholdersAndLinks(sender, c);
		json += "]";
		if (getInstance().getConfig().getString("loglevel", "info").equals("debug")) {
			System.out.println(json);
			System.out.println("END OF JSON");
			System.out.println("END OF JSON");
			System.out.println("END OF JSON");
			System.out.println("END OF JSON");
			System.out.println("END OF JSON");
		}
		return json;
	}

	/**
     * Converts the format section of a message to JSON using PlaceholderAPI.
     *
     * @param s
     * @param format
     * @param prefix
     * @param nickname
     * @param suffix
     * @param icp
     * @return {@link String}
     */
	private static String convertPlaceholders(String s, JsonFormat format, MineverseChatPlayer icp) {
		String remaining = s;
		String temp = "";
		int indexStart = -1;
		int indexEnd = -1;
		String placeholder = "";
		String formattedPlaceholder = "";
		String lastCode = DEFAULT_COLOR_CODE;
		do {
			Matcher matcher = PLACEHOLDERAPI_PLACEHOLDER_PATTERN.matcher(remaining);
			if (matcher.find()) {
				indexStart = matcher.start();
				indexEnd = matcher.end();
				placeholder = remaining.substring(indexStart, indexEnd);
				formattedPlaceholder = escapeJsonChars(Format.FormatStringAll(Format.applyAllPlaceholders(icp.getPlayer(), placeholder)));
				temp += convertToJsonColors(escapeJsonChars(lastCode + remaining.substring(0, indexStart))) + ",";
				lastCode = getLastCode(lastCode + remaining.substring(0, indexStart));
				boolean placeholderHasJsonAttribute = false;
				for (JsonAttribute jsonAttribute : format.getJsonAttributes()) {
					if (placeholder.contains(jsonAttribute.getName().replace("{", "").replace("}", ""))) {
						final StringBuilder hover = new StringBuilder();
						for (String st : jsonAttribute.getHoverText()) {
							hover.append(Format.FormatStringAll(st) + "\n");
						}
						final String hoverText;
						if(!hover.isEmpty()) {
							hoverText = escapeJsonChars(Format.FormatStringAll(
									Format.applyAllPlaceholders(icp.getPlayer(), hover.substring(0, hover.length() - 1))));
						} else {
							hoverText = StringUtils.EMPTY;
						}
						final ClickAction clickAction = jsonAttribute.getClickAction();
						final String actionJson;
						if (clickAction == ClickAction.NONE) {
							actionJson = StringUtils.EMPTY;
						} else {
							final String clickText = escapeJsonChars(Format.FormatStringAll(
									Format.applyAllPlaceholders(icp.getPlayer(), jsonAttribute.getClickText())));
							actionJson = ",\"click_event\":{\"action\":\"" + jsonAttribute.getClickAction().toString() + "\",\"command\":\"" + clickText
							+ "\"}";
						}
						final String hoverJson;
						if (hoverText.isEmpty()) {
							hoverJson = StringUtils.EMPTY;
						} else {
							hoverJson = ",\"hover_event\":{\"action\":\"show_text\",\"value\":{\"text\":\"\",\"extra\":["
									+ convertToJsonColors(hoverText) + "]}}";
						}
						temp += convertToJsonColors(lastCode + formattedPlaceholder, actionJson + hoverJson) + ",";
						placeholderHasJsonAttribute = true;
						break;
					}
				}
				if (!placeholderHasJsonAttribute) {
					temp += convertToJsonColors(lastCode + formattedPlaceholder) + ",";
				}
				lastCode = getLastCode(lastCode + formattedPlaceholder);
				remaining = remaining.substring(indexEnd);
			} else {
				temp += convertToJsonColors(lastCode + remaining);
				break;
			}
		} while (true);
		return temp;
	}

	/**
     * Converts URL's to JSON.
     *
     * @param s
     * @return {@link String}
     */
	private static String convertLinks(String s) {
		String remaining = s;
		String temp = "";
		int indexLink = -1;
		int indexLinkEnd = -1;
		String link = "";
		String lastCode = DEFAULT_COLOR_CODE;
		do {
			Pattern pattern = Pattern.compile(
					"([a-zA-Z0-9" + BUKKIT_COLOR_CODE_PREFIX + "\\-:/]+\\.[a-zA-Z/0-9" + BUKKIT_COLOR_CODE_PREFIX
							+ "\\-:_#]+(\\.[a-zA-Z/0-9." + BUKKIT_COLOR_CODE_PREFIX + "\\-:;,#\\?\\+=_]+)?)");
			Matcher matcher = pattern.matcher(remaining);
			if (matcher.find()) {
				indexLink = matcher.start();
				indexLinkEnd = matcher.end();
				link = remaining.substring(indexLink, indexLinkEnd);
				temp += convertToJsonColors(lastCode + remaining.substring(0, indexLink)) + ",";
				lastCode = getLastCode(lastCode + remaining.substring(0, indexLink));
				String https = "";
				if (ChatColor.stripColor(link).contains("https://"))
					https = "s";
				temp += convertToJsonColors(lastCode + link,
						",\"underlined\":" + underlineURLs()
								+ ",\"click_event\":{\"action\":\"open_url\",\"url\":\"http" + https + "://"
								+ ChatColor.stripColor(link.replace("http://", "").replace("https://", ""))
								+ "\"},\"hover_event\":{\"action\":\"show_text\",\"value\":{\"text\":\"\",\"extra\":["
								+ convertToJsonColors(lastCode + link) + "]}}")
						+ ",";
				lastCode = getLastCode(lastCode + link);
				remaining = remaining.substring(indexLinkEnd);
			} else {
				temp += convertToJsonColors(lastCode + remaining);
				break;
			}
		} while (true);
		return temp;
	}

	private static final String ITEM_CHAT_CONFIG_PATH = "itemchat";

	/**
	 * Returns true when the item chat feature is enabled, the sender is allowed
	 * to use it, and {@code message} contains at least one configured
	 * placeholder. Used to decide whether a private message should be sent as
	 * a raw string or through the JSON packet path to render the item preview.
	 */
	public static boolean containsItemChatPlaceholder(MineverseChatPlayer sender, String message) {
		if (message == null || message.isEmpty()) {
			return false;
		}
		if (sender == null || sender.getPlayer() == null
				|| !getInstance().getConfig().getBoolean(ITEM_CHAT_CONFIG_PATH + ".enabled", false)) {
			return false;
		}
		List<String> placeholders = getInstance().getConfig().getStringList(ITEM_CHAT_CONFIG_PATH + ".placeholders");
		if (placeholders == null || placeholders.isEmpty()) {
			return false;
		}
		String permission = getInstance().getConfig().getString(ITEM_CHAT_CONFIG_PATH + ".permission", "None");
		if (permission != null && !permission.isEmpty() && !permission.equalsIgnoreCase("None")
				&& !sender.getPlayer().hasPermission(permission)) {
			return false;
		}
		for (String placeholder : placeholders) {
			if (placeholder != null && !placeholder.isEmpty() && message.contains(placeholder)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Sends a private-message-style chat line to {@code recipient}. If the
	 * sender's message contains an item chat placeholder it is rendered as a
	 * JSON component with an item preview hover; otherwise the traditional
	 * {@link Player#sendMessage(String)} path is used to preserve the exact
	 * legacy behavior for players who do not use the feature.
	 *
	 * @param sender    the player the message originates from (for held item lookup).
	 * @param recipient the player who will receive the rendered message.
	 * @param prefix    the pre-formatted prefix text (already colored, no placeholders remaining).
	 * @param message   the pre-formatted message body (already colored).
	 */
	public static void sendPrivateMessage(MineverseChatPlayer sender, Player recipient, String prefix, String message) {
		if (recipient == null) {
			return;
		}
		if (!containsItemChatPlaceholder(sender, message)) {
			recipient.sendMessage(prefix + message);
			return;
		}
		String json = convertPrivateMessageToJson(sender, prefix, message);
		PacketContainer packet = createPacketPlayOutChat(json);
		sendPacketPlayOutChat(recipient, packet);
	}

	private static String convertPrivateMessageToJson(MineverseChatPlayer sender, String prefix, String message) {
		String safePrefix = prefix == null ? "" : prefix;
		String safeMessage = message == null ? "" : message;
		String escapedPrefix = escapeJsonChars(safePrefix);
		String escapedMessage = escapeJsonChars(safeMessage);
		String prefixLastCode = getLastCode(DEFAULT_COLOR_CODE + escapedPrefix);
		if (prefixLastCode == null || prefixLastCode.isEmpty()) {
			prefixLastCode = DEFAULT_COLOR_CODE;
		}
		StringBuilder json = new StringBuilder();
		json.append("[\"\",");
		json.append(convertToJsonColors(DEFAULT_COLOR_CODE + escapedPrefix));
		json.append(',');
		json.append(convertItemPlaceholdersAndLinks(sender, prefixLastCode + escapedMessage));
		json.append(']');
		return json.toString();
	}

	/**
	 * Same as {@link #convertLinks(String)}, but also replaces item chat
	 * placeholders configured under {@code itemchat.placeholders} with a JSON
	 * component that shows on hover the name and lore of the item currently
	 * held in the sender's main hand.
	 */
	private static String convertItemPlaceholdersAndLinks(MineverseChatPlayer sender, String s) {
		if (sender == null || sender.getPlayer() == null
				|| !getInstance().getConfig().getBoolean(ITEM_CHAT_CONFIG_PATH + ".enabled", false)) {
			return convertLinks(s);
		}
		List<String> placeholders = getInstance().getConfig().getStringList(ITEM_CHAT_CONFIG_PATH + ".placeholders");
		if (placeholders == null || placeholders.isEmpty()) {
			return convertLinks(s);
		}
		String permission = getInstance().getConfig().getString(ITEM_CHAT_CONFIG_PATH + ".permission", "None");
		if (permission != null && !permission.isEmpty() && !permission.equalsIgnoreCase("None")
				&& !sender.getPlayer().hasPermission(permission)) {
			return convertLinks(s);
		}

		StringBuilder patternBuilder = new StringBuilder();
		boolean addedAny = false;
		for (String placeholder : placeholders) {
			if (placeholder == null || placeholder.isEmpty()) {
				continue;
			}
			if (addedAny) {
				patternBuilder.append('|');
			}
			patternBuilder.append(Pattern.quote(placeholder));
			addedAny = true;
		}
		if (!addedAny) {
			return convertLinks(s);
		}

		Pattern pattern = Pattern.compile(patternBuilder.toString());
		Matcher matcher = pattern.matcher(s);
		if (!matcher.find()) {
			return convertLinks(s);
		}
		matcher.reset();

		ItemStack heldItem = resolveHeldItem(sender);
		String itemJson = buildItemChatJson(sender, heldItem);

		StringBuilder result = new StringBuilder();
		int lastEnd = 0;
		String lastCode = DEFAULT_COLOR_CODE;
		boolean firstPart = true;
		while (matcher.find()) {
			int start = matcher.start();
			int end = matcher.end();
			if (start > lastEnd) {
				String segment = s.substring(lastEnd, start);
				if (!firstPart) {
					result.append(',');
				}
				result.append(convertLinks(lastCode + segment));
				lastCode = getLastCode(lastCode + segment);
				firstPart = false;
			}
			if (!firstPart) {
				result.append(',');
			}
			result.append(itemJson);
			firstPart = false;
			lastEnd = end;
		}
		if (lastEnd < s.length()) {
			String segment = s.substring(lastEnd);
			if (!firstPart) {
				result.append(',');
			}
			result.append(convertLinks(lastCode + segment));
		}
		return result.toString();
	}

	/**
	 * Returns the item to preview for the sender. Tries a live main-hand read
	 * first — safe when we are on the sender's owning thread (main on Paper,
	 * region thread on Folia when the call originates from a command) — and
	 * falls back to the snapshot cached at chat dispatch time when the live
	 * read is not allowed (typically async chat handlers on Folia). The
	 * returned {@link ItemStack} is always an independent copy.
	 */
	private static ItemStack resolveHeldItem(MineverseChatPlayer sender) {
		if (sender == null || sender.getPlayer() == null) {
			return null;
		}
		try {
			ItemStack live = sender.getPlayer().getInventory().getItemInMainHand();
			return live == null ? null : live.clone();
		} catch (Exception ignored) {
			ItemStack snapshot = sender.getChatHeldItemSnapshot();
			return snapshot == null ? null : snapshot.clone();
		}
	}

	/**
	 * Builds the JSON component representing the item-chat placeholder for a
	 * given held item. The visible text comes from {@code itemchat.format} (or
	 * {@code itemchat.emptyformat} when the hand is empty) and the hover text
	 * lists the item's display name followed by its lore lines.
	 */
	private static String buildItemChatJson(MineverseChatPlayer sender, ItemStack item) {
		Player senderPlayer = (sender == null) ? null : sender.getPlayer();
		boolean isEmpty = (item == null || item.getType() == Material.AIR);
		String format;
		String itemName;
		int amount;
		Material type;
		int maxNameChars = getInstance().getConfig().getInt(ITEM_CHAT_CONFIG_PATH + ".max_name_length", 100);
		if (isEmpty) {
			format = getInstance().getConfig().getString(ITEM_CHAT_CONFIG_PATH + ".emptyformat", "&8[&7empty hand&8]");
			itemName = "";
			amount = 0;
			type = Material.AIR;
		} else {
			format = getInstance().getConfig().getString(ITEM_CHAT_CONFIG_PATH + ".format", "&e[&f{item_name}&e]");
			itemName = truncate(getItemDisplayName(item), maxNameChars);
			amount = item.getAmount();
			type = item.getType();
			if (amount > 1) {
				format += getInstance().getConfig().getString(ITEM_CHAT_CONFIG_PATH + ".amountsuffix", " &7x{item_amount}");
			}
		}

		String rawText = format
				.replace("{item_name}", itemName)
				.replace("{item_amount}", String.valueOf(amount))
				.replace("{item_type}", type.name());
		String text = applyNexoGlyphPlaceholders(senderPlayer, FormatStringAll(rawText));
		if (!text.startsWith(BUKKIT_COLOR_CODE_PREFIX)) {
			text = DEFAULT_COLOR_CODE + text;
		}
		String escapedText = escapeJsonChars(text);

		if (isEmpty) {
			return convertToJsonColors(escapedText);
		}

		// Prefer the native show_item hover so the client renders the actual
		// item tooltip. This carries over Nexo tooltype styling, custom model
		// data, glyphs, enchantment lines and every data component without us
		// having to reconstruct them by hand.
		String nativeHover = buildShowItemJson(text, item);
		if (nativeHover != null) {
			return nativeHover;
		}
		// Fallback: rebuild the tooltip ourselves as show_text.
		return buildShowTextFallback(escapedText, senderPlayer, itemName, getItemLore(item));
	}

	private static String buildShowItemJson(String visibleLegacyText, ItemStack item) {
		try {
			Component visible = LegacyComponentSerializer.legacySection().deserialize(visibleLegacyText);
			HoverEvent<?> hover = item.asHoverEvent();
			Component withHover = visible.hoverEvent(hover);
			String serialized = GsonComponentSerializer.gson().serialize(withHover);
			if (serialized == null || serialized.isEmpty()) {
				return null;
			}
			return serialized;
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static String buildShowTextFallback(String escapedVisibleText, Player senderPlayer, String itemName, List<String> lore) {
		int maxLoreLines = getInstance().getConfig().getInt(ITEM_CHAT_CONFIG_PATH + ".max_lore_lines", 30);
		int maxLoreChars = getInstance().getConfig().getInt(ITEM_CHAT_CONFIG_PATH + ".max_lore_line_length", 250);

		StringBuilder hoverExtra = new StringBuilder();
		String nameLine = applyNexoGlyphPlaceholders(senderPlayer, FormatStringAll(itemName));
		if (nameLine.indexOf(BUKKIT_COLOR_CODE_PREFIX_CHAR) < 0) {
			nameLine = BUKKIT_COLOR_CODE_PREFIX + "f" + BUKKIT_COLOR_CODE_PREFIX + "o" + nameLine;
		}
		hoverExtra.append(convertToJsonColors(escapeJsonChars(nameLine)));
		if (lore != null) {
			int emitted = 0;
			for (String loreLine : lore) {
				if (loreLine == null) {
					continue;
				}
				if (emitted >= maxLoreLines) {
					String cutoff = FormatStringAll("&8&o... &7(" + (lore.size() - emitted) + " more)");
					hoverExtra.append(",{\"text\":\"\\n\"},");
					hoverExtra.append(convertToJsonColors(escapeJsonChars(cutoff)));
					break;
				}
				String truncated = truncate(loreLine, maxLoreChars);
				String rendered = applyNexoGlyphPlaceholders(senderPlayer, FormatStringAll(truncated));
				if (rendered.indexOf(BUKKIT_COLOR_CODE_PREFIX_CHAR) < 0) {
					rendered = BUKKIT_COLOR_CODE_PREFIX + "5" + BUKKIT_COLOR_CODE_PREFIX + "o" + rendered;
				}
				hoverExtra.append(",{\"text\":\"\\n\"},");
				hoverExtra.append(convertToJsonColors(escapeJsonChars(rendered)));
				emitted++;
			}
		}

		String hoverExt = ",\"hover_event\":{\"action\":\"show_text\",\"value\":{\"text\":\"\",\"extra\":["
				+ hoverExtra + "]}}";
		return convertToJsonColors(escapedVisibleText, hoverExt);
	}

	private static String getItemDisplayName(ItemStack item) {
		ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
		if (meta != null) {
			// Prefer the modern Adventure display name (respects Component-based
			// formatting including glyph tags Nexo may embed as Component payloads).
			try {
				Component displayComponent = meta.displayName();
				if (displayComponent != null) {
					String legacy = LegacyComponentSerializer.legacySection().serialize(displayComponent);
					if (!legacy.isEmpty()) {
						return legacy;
					}
				}
			} catch (Throwable ignored) {
			}
			// Legacy String display name (still populated by many plugins).
			if (meta.hasDisplayName()) {
				String display = meta.getDisplayName();
				if (display != null && !display.isEmpty()) {
					return display;
				}
			}
			// 1.20.5+ minecraft:item_name component — used by Nexo and other
			// modern plugins that ship items with an immutable display name.
			try {
				if (meta.hasItemName()) {
					Component itemNameComponent = meta.itemName();
					if (itemNameComponent != null) {
						String legacy = LegacyComponentSerializer.legacySection().serialize(itemNameComponent);
						if (!legacy.isEmpty()) {
							return legacy;
						}
					}
				}
			} catch (Throwable ignored) {
			}
		}
		// Paper API: ItemStack#effectiveName() / displayName() resolve the actual
		// tooltip title including the minecraft:item_name data component and any
		// vanilla translation key. This catches Nexo items whose name lives in
		// a data component that ItemMeta does not expose directly.
		try {
			java.lang.reflect.Method effective = item.getClass().getMethod("effectiveName");
			Object result = effective.invoke(item);
			if (result instanceof Component) {
				String legacy = LegacyComponentSerializer.legacySection().serialize((Component) result);
				if (!legacy.isEmpty()) {
					return legacy;
				}
			}
		} catch (Throwable ignored) {
		}
		try {
			java.lang.reflect.Method displayName = item.getClass().getMethod("displayName");
			Object result = displayName.invoke(item);
			if (result instanceof Component) {
				String legacy = LegacyComponentSerializer.legacySection().serialize((Component) result);
				// ItemStack#displayName wraps its output in "[name]" for chat use.
				if (legacy.startsWith("[") && legacy.endsWith("]") && legacy.length() > 2) {
					legacy = legacy.substring(1, legacy.length() - 1);
				}
				if (!legacy.isEmpty()) {
					return legacy;
				}
			}
		} catch (Throwable ignored) {
		}
		// Ask Nexo directly if the plugin is installed and this is one of its items.
		String nexoName = resolveNexoItemName(item);
		if (nexoName != null && !nexoName.isEmpty()) {
			return nexoName;
		}
		return prettifyMaterialName(item.getType());
	}

	// Last-resort reflection into Nexo's public API to recover the display
	// name of a custom item. Returns null when Nexo is absent, the item is
	// vanilla, or the API shape does not match. Never throws.
	private static String resolveNexoItemName(ItemStack item) {
		if (item == null) {
			return null;
		}
		try {
			if (!Bukkit.getPluginManager().isPluginEnabled("Nexo")) {
				return null;
			}
			Class<?> nexoItems = Class.forName("com.nexomc.nexo.api.NexoItems");
			Object idObj = nexoItems.getMethod("idFromItem", ItemStack.class).invoke(null, item);
			if (idObj == null) {
				return null;
			}
			Object builder = nexoItems.getMethod("itemFromId", String.class).invoke(null, idObj);
			if (builder == null) {
				return null;
			}
			for (String methodName : new String[] { "displayName", "getDisplayName", "itemName", "getItemName" }) {
				try {
					java.lang.reflect.Method m = builder.getClass().getMethod(methodName);
					Object result = m.invoke(builder);
					if (result instanceof Component) {
						String legacy = LegacyComponentSerializer.legacySection().serialize((Component) result);
						if (!legacy.isEmpty()) {
							return legacy;
						}
					} else if (result instanceof String && !((String) result).isEmpty()) {
						return (String) result;
					}
				} catch (NoSuchMethodException ignored) {
				}
			}
			// Fall back to the Nexo item ID prettified.
			if (idObj instanceof String) {
				String id = (String) idObj;
				return prettifyIdentifier(id);
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	private static String prettifyIdentifier(String id) {
		if (id == null || id.isEmpty()) {
			return "";
		}
		String[] words = id.toLowerCase().replace('-', '_').split("_");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < words.length; i++) {
			if (i > 0) {
				sb.append(' ');
			}
			if (!words[i].isEmpty()) {
				sb.append(Character.toUpperCase(words[i].charAt(0)));
				if (words[i].length() > 1) {
					sb.append(words[i].substring(1));
				}
			}
		}
		return sb.toString();
	}

	private static List<String> getItemLore(ItemStack item) {
		if (!item.hasItemMeta()) {
			return null;
		}
		ItemMeta meta = item.getItemMeta();
		if (meta == null) {
			return null;
		}
		// Adventure lore first (preserves Component formatting for modern plugins).
		try {
			List<Component> componentLore = meta.lore();
			if (componentLore != null && !componentLore.isEmpty()) {
				List<String> serialized = new ArrayList<>(componentLore.size());
				for (Component line : componentLore) {
					if (line == null) {
						continue;
					}
					serialized.add(LegacyComponentSerializer.legacySection().serialize(line));
				}
				if (!serialized.isEmpty()) {
					return serialized;
				}
			}
		} catch (Throwable ignored) {
		}
		if (meta.hasLore()) {
			return meta.getLore();
		}
		return null;
	}

	private static String truncate(String input, int maxChars) {
		if (input == null) {
			return "";
		}
		if (maxChars <= 0 || input.length() <= maxChars) {
			return input;
		}
		return input.substring(0, maxChars) + "…";
	}

	private static String prettifyMaterialName(Material material) {
		String[] words = material.name().toLowerCase().split("_");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < words.length; i++) {
			if (i > 0) {
				sb.append(' ');
			}
			if (!words[i].isEmpty()) {
				sb.append(Character.toUpperCase(words[i].charAt(0)));
				if (words[i].length() > 1) {
					sb.append(words[i].substring(1));
				}
			}
		}
		return sb.toString();
	}

	public static String getLastCode(String s) {
		String ts = "";
		char[] ch = s.toCharArray();
		for (int a = 0; a < s.length() - 1; a++) {
			if (String.valueOf(ch[a + 1]).matches("[lkomnLKOMN]") && ch[a] == BUKKIT_COLOR_CODE_PREFIX_CHAR) {
				ts += String.valueOf(ch[a]) + ch[a + 1];
				a++;
			} else if (String.valueOf(ch[a + 1]).matches("[0123456789abcdefrABCDEFR]")
					&& ch[a] == BUKKIT_COLOR_CODE_PREFIX_CHAR) {
				ts = String.valueOf(ch[a]) + ch[a + 1];
				a++;
			} else if (ch[a + 1] == 'x' && ch[a] == BUKKIT_COLOR_CODE_PREFIX_CHAR) {
				if (ch.length > a + 13) {
					if (String.valueOf(ch[a + 3]).matches("[0123456789abcdefABCDEF]")
							&& String.valueOf(ch[a + 5]).matches("[0123456789abcdefABCDEF]")
							&& String.valueOf(ch[a + 7]).matches("[0123456789abcdefABCDEF]")
							&& String.valueOf(ch[a + 9]).matches("[0123456789abcdefABCDEF]")
							&& String.valueOf(ch[a + 11]).matches("[0123456789abcdefABCDEF]")
							&& String.valueOf(ch[a + 13]).matches("[0123456789abcdefABCDEF]")
							&& ch[a + 2] == BUKKIT_COLOR_CODE_PREFIX_CHAR && ch[a + 4] == BUKKIT_COLOR_CODE_PREFIX_CHAR
							&& ch[a + 6] == BUKKIT_COLOR_CODE_PREFIX_CHAR && ch[a + 8] == BUKKIT_COLOR_CODE_PREFIX_CHAR
							&& ch[a + 10] == BUKKIT_COLOR_CODE_PREFIX_CHAR
							&& ch[a + 12] == BUKKIT_COLOR_CODE_PREFIX_CHAR) {
						ts = String.valueOf(ch[a]) + ch[a + 1] + ch[a + 2] + ch[a + 3] + ch[a + 4] + ch[a + 5]
								+ ch[a + 6] + ch[a + 7] + ch[a + 8] + ch[a + 9] + ch[a + 10] + ch[a + 11] + ch[a + 12]
								+ ch[a + 13];
						a += 13;
					}
				}
			}
		}
		return ts;
	}

	/**
     * Converts a message to JSON colors with no additional JSON extensions.
     *
     * @param s
     * @return {@link String}
     */
	public static String convertToJsonColors(String s) {
		return convertToJsonColors(s, "");
	}

	/**
     * Converts a message to JSON colors with additional JSON extensions.
     *
     * @param s
     * @param extensions
     * @return {@link String}
     */
	private static String convertToJsonColors(String s, String extensions) {
		String remaining = s;
		String temp = "";
		int indexColor = -1;
		int indexNextColor = -1;
		String color = "";
		String modifier = "";
		boolean bold = false;
		boolean obfuscated = false;
		boolean italic = false;
		boolean strikethrough = false;
		boolean underlined = false;
		String previousColor = "";
		int colorLength = LEGACY_COLOR_CODE_LENGTH;
		do {
			if (remaining.length() < LEGACY_COLOR_CODE_LENGTH) {
				temp = "{\"text\":\"" + remaining + "\"},";
				break;
			}
			modifier = "";
			indexColor = remaining.indexOf(BUKKIT_COLOR_CODE_PREFIX);
			previousColor = color;

			color = remaining.substring(1, indexColor + LEGACY_COLOR_CODE_LENGTH);
			if (color.equals(BUKKIT_HEX_COLOR_CODE_PREFIX)) {
				if (remaining.length() >= HEX_COLOR_CODE_LENGTH) {
					color = HEX_COLOR_CODE_PREFIX
							+ remaining.substring(LEGACY_COLOR_CODE_LENGTH, indexColor + HEX_COLOR_CODE_LENGTH)
									.replace(BUKKIT_COLOR_CODE_PREFIX, "");
					colorLength = HEX_COLOR_CODE_LENGTH;
					bold = false;
					obfuscated = false;
					italic = false;
					strikethrough = false;
					underlined = false;
				}
			} else if (!color.matches("[0123456789abcdefABCDEF]")) {
				switch (color) {
				case "l":
				case "L": {
					bold = true;
					break;
				}
				case "k":
				case "K": {
					obfuscated = true;
					break;
				}
				case "o":
				case "O": {
					italic = true;
					break;
				}
				case "m":
				case "M": {
					strikethrough = true;
					break;
				}
				case "n":
				case "N": {
					underlined = true;
					break;
				}
				case "r":
				case "R": {
					bold = false;
					obfuscated = false;
					italic = false;
					strikethrough = false;
					underlined = false;
					color = "f";
					break;
				}
				}
				if (!color.equals("f"))
					color = previousColor;
				if (color.length() == 0)
					color = "f";
			} else {
				bold = false;
				obfuscated = false;
				italic = false;
				strikethrough = false;
				underlined = false;
			}
			if (bold)
				if (VersionHandler.isAtLeast_1_20_4()) {
					modifier += ",\"bold\":true";
				} else {
					modifier += ",\"bold\":\"true\"";
				}
			if (obfuscated)
				if (VersionHandler.isAtLeast_1_20_4()) {
					modifier += ",\"obfuscated\":true";
				} else {
					modifier += ",\"obfuscated\":\"true\"";
				}
			if (italic)
				if (VersionHandler.isAtLeast_1_20_4()) {
					modifier += ",\"italic\":true";
				} else {
					modifier += ",\"italic\":\"true\"";
				}
			if (underlined)
				if (VersionHandler.isAtLeast_1_20_4()) {
					modifier += ",\"underlined\":true";
				} else {
					modifier += ",\"underlined\":\"true\"";
				}
			if (strikethrough)
				if (VersionHandler.isAtLeast_1_20_4()) {
					modifier += ",\"strikethrough\":true";
				} else {
					modifier += ",\"strikethrough\":\"true\"";
				}
			remaining = remaining.substring(colorLength);
			colorLength = LEGACY_COLOR_CODE_LENGTH;
			indexNextColor = remaining.indexOf(BUKKIT_COLOR_CODE_PREFIX);
			if (indexNextColor == -1) {
				indexNextColor = remaining.length();
			}
			temp += "{\"text\":\"" + remaining.substring(0, indexNextColor) + "\",\"color\":\""
					+ hexidecimalToJsonColorRGB(color) + "\"" + modifier + extensions + "},";
			remaining = remaining.substring(indexNextColor);
		} while (remaining.length() > 1 && indexColor != -1);
		if (temp.length() > 1)
			temp = temp.substring(0, temp.length() - 1);
		return temp;
	}

	private static String hexidecimalToJsonColorRGB(String c) {
		if (c.length() == 1) {
			switch (c) {
			case "0":
				return "black";
			case "1":
				return "dark_blue";
			case "2":
				return "dark_green";
			case "3":
				return "dark_aqua";
			case "4":
				return "dark_red";
			case "5":
				return "dark_purple";
			case "6":
				return "gold";
			case "7":
				return "gray";
			case "8":
				return "dark_gray";
			case "9":
				return "blue";
			case "a":
			case "A":
				return "green";
			case "b":
			case "B":
				return "aqua";
			case "c":
			case "C":
				return "red";
			case "d":
			case "D":
				return "light_purple";
			case "e":
			case "E":
				return "yellow";
			case "f":
			case "F":
				return "white";
			default:
				return "white";
			}
		}
		if (isValidHexColor(c)) {
			return c;
		}
		return "white";
	}

	public static String convertPlainTextToJson(String s, boolean convertURL) {
		s = escapeJsonChars(s);
		if (convertURL) {
			return "[" + Format.convertLinks(s) + "]";
		} else {
			return "[" + convertToJsonColors(DEFAULT_COLOR_CODE + s) + "]";
		}
	}
	
	private static String escapeJsonChars(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	public static String formatModerationGUI(String json, Player player, String sender, String channelName, int hash) {
		if (player.hasPermission("venturechat.gui")) {
			json = json.substring(0, json.length() - 1);
			json += "," + Format.convertToJsonColors(Format.FormatStringAll(getInstance().getConfig().getString("guiicon")),
					",\"click_event\":{\"action\":\"run_command\",\"command\":\"/vchatgui " + sender + " " + channelName
							+ " " + hash
							+ "\"},\"hover_event\":{\"action\":\"show_text\",\"value\":{\"text\":\"\",\"extra\":["
							+ Format.convertToJsonColors(
									Format.FormatStringAll(getInstance().getConfig().getString("guitext")))
							+ "]}}")
					+ "]";
		}
		return json;
	}

	public static PacketContainer createPacketPlayOutChat(String json) {
		final PacketContainer container;
		if (VersionHandler.isAtLeast_1_20_4()) { // 1.20.4+
			container = new PacketContainer(PacketType.Play.Server.SYSTEM_CHAT);
			container.getChatComponents().write(0, WrappedChatComponent.fromJson(json));
			container.getBooleans().write(0, false);
		} else if (VersionHandler.isAbove_1_19()) { // 1.19.1 -> 1.20.3
			container = new PacketContainer(PacketType.Play.Server.SYSTEM_CHAT);
			container.getStrings().write(0, json);
			container.getBooleans().write(0, false);
		} else if (VersionHandler.isUnder_1_19()) { // 1.7 -> 1.19
			WrappedChatComponent component = WrappedChatComponent.fromJson(json);
			container = new PacketContainer(PacketType.Play.Server.CHAT);
			container.getModifier().writeDefaults();
			container.getChatComponents().write(0, component);
		} else { // 1.19
			container = new PacketContainer(PacketType.Play.Server.SYSTEM_CHAT);
			container.getStrings().write(0, json);
			container.getIntegers().write(0, 1);
		}
		return container;
	}

	public static PacketContainer createPacketPlayOutChat(WrappedChatComponent component) {
		final PacketContainer container;
		if (VersionHandler.isAtLeast_1_20_4()) { // 1.20.4+
			container = new PacketContainer(PacketType.Play.Server.SYSTEM_CHAT);
			container.getChatComponents().write(0, component);
			container.getBooleans().write(0, false);
		} else if (VersionHandler.isAbove_1_19()) { // 1.19.1 -> 1.20.3
			container = new PacketContainer(PacketType.Play.Server.SYSTEM_CHAT);
			container.getStrings().write(0, component.getJson());
			container.getBooleans().write(0, false);
		} else if (VersionHandler.isUnder_1_19()) { // 1.7 -> 1.19
			container = new PacketContainer(PacketType.Play.Server.CHAT);
			container.getModifier().writeDefaults();
			container.getChatComponents().write(0, component);
		} else { // 1.19
			container = new PacketContainer(PacketType.Play.Server.SYSTEM_CHAT);
			container.getStrings().write(0, component.getJson());
			container.getIntegers().write(0, 1);
		}
		return container;
	}

	public static void sendPacketPlayOutChat(Player player, PacketContainer packet) {
		try {
			ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("unchecked")
	public static String toColoredText(Object o, Class<?> c) {
		if (VersionHandler.is1_7()) {
			return "\"extra\":[{\"text\":\"Hover to see original message is not currently supported in 1.7\",\"color\":\"red\"}]";
		} 
		List<Object> finalList = new ArrayList<>();
		StringBuilder stringbuilder = new StringBuilder();
		stringbuilder.append("\"extra\":[");
		try {
			splitComponents(finalList, o, c);
			for (Object component : finalList) {		
				try {
					if (VersionHandler.is1_8() || VersionHandler.is1_9() || VersionHandler.is1_10() || VersionHandler.is1_11() || VersionHandler.is1_12() || VersionHandler.is1_13() || VersionHandler.is1_14() || VersionHandler.is1_15() || VersionHandler.is1_16() || VersionHandler.is1_17()) {
						String text = (String) component.getClass().getMethod("getText").invoke(component);
						Object chatModifier = component.getClass().getMethod("getChatModifier").invoke(component);
						Object color = chatModifier.getClass().getMethod("getColor").invoke(chatModifier);
						String colorString = "white";
						if (color != null ) {
							colorString = color.getClass().getMethod("b").invoke(color).toString();
						}
						boolean bold = (boolean) chatModifier.getClass().getMethod("isBold").invoke(chatModifier);
						boolean strikethrough = (boolean) chatModifier.getClass().getMethod("isStrikethrough").invoke(chatModifier);
						boolean italic = (boolean) chatModifier.getClass().getMethod("isItalic").invoke(chatModifier);
						boolean underlined = (boolean) chatModifier.getClass().getMethod("isUnderlined").invoke(chatModifier);
						boolean obfuscated = (boolean) chatModifier.getClass().getMethod("isRandom").invoke(chatModifier);
						JSONObject jsonObject = new JSONObject();
						jsonObject.put("text", text);
						jsonObject.put("color", colorString);
						jsonObject.put("bold", bold);
						jsonObject.put("strikethrough", strikethrough);
						jsonObject.put("italic", italic);
						jsonObject.put("underlined", underlined);
						jsonObject.put("obfuscated", obfuscated);
						stringbuilder.append(jsonObject.toJSONString() + ",");
					} else {
						String text = (String) component.getClass().getMethod("getString").invoke(component);
						Object chatModifier = component.getClass().getMethod("c").invoke(component);
						Object color = chatModifier.getClass().getMethod("a").invoke(chatModifier);
						String colorString = "white";
						if (color != null ) {
							colorString = color.getClass().getMethod("b").invoke(color).toString();
						}
						boolean bold = (boolean) chatModifier.getClass().getMethod("b").invoke(chatModifier);
						boolean italic = (boolean) chatModifier.getClass().getMethod("c").invoke(chatModifier);
						boolean strikethrough = (boolean) chatModifier.getClass().getMethod("d").invoke(chatModifier);
						boolean underlined = (boolean) chatModifier.getClass().getMethod("e").invoke(chatModifier);
						boolean obfuscated = (boolean) chatModifier.getClass().getMethod("f").invoke(chatModifier);
						JSONObject jsonObject = new JSONObject();
						jsonObject.put("text", text);
						jsonObject.put("color", colorString);
						jsonObject.put("bold", bold);
						jsonObject.put("strikethrough", strikethrough);
						jsonObject.put("italic", italic);
						jsonObject.put("underlined", underlined);
						jsonObject.put("obfuscated", obfuscated);
						stringbuilder.append(jsonObject.toJSONString() + ",");
					}
				}
				catch(Exception e) {
					return "\"extra\":[{\"text\":\"Something went wrong. Could not access color.\",\"color\":\"red\"}]";
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		String coloredText = stringbuilder.toString();
		if(coloredText.endsWith(",")) {
			coloredText = coloredText.substring(0, coloredText.length() - 1);
		}
		coloredText += "]";
		return coloredText;
	}

	public static String toPlainText(Object o, Class<?> c) {
		List<Object> finalList = new ArrayList<>();
		StringBuilder stringbuilder = new StringBuilder();
		try {
			splitComponents(finalList, o, c);
			for (Object component : finalList) {
				if (VersionHandler.is1_7()) {
					stringbuilder.append((String) component.getClass().getMethod("e").invoke(component));
				} else if(VersionHandler.is1_8() || VersionHandler.is1_9() || VersionHandler.is1_10() || VersionHandler.is1_11() || VersionHandler.is1_12() || VersionHandler.is1_13() || VersionHandler.is1_14() || VersionHandler.is1_15() || VersionHandler.is1_16() || VersionHandler.is1_17()){
					stringbuilder.append((String) component.getClass().getMethod("getText").invoke(component));
				}
				else {
					stringbuilder.append((String) component.getClass().getMethod("getString").invoke(component));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return stringbuilder.toString();
	}

	private static void splitComponents(List<Object> finalList, Object o, Class<?> c) throws Exception {
		if (VersionHandler.is1_7() || VersionHandler.is1_8() || VersionHandler.is1_9() || VersionHandler.is1_10()
				|| VersionHandler.is1_11() || VersionHandler.is1_12() || VersionHandler.is1_13()
				|| (VersionHandler.is1_14() && !VersionHandler.is1_14_4())) {
			ArrayList<?> list = (ArrayList<?>) c.getMethod("a").invoke(o, new Object[0]);
			for (Object component : list) {
				ArrayList<?> innerList = (ArrayList<?>) c.getMethod("a").invoke(component, new Object[0]);
				if (innerList.size() > 0) {
					splitComponents(finalList, component, c);
				} else {
					finalList.add(component);
				}
			}
		} else if(VersionHandler.is1_14_4() || VersionHandler.is1_15() || VersionHandler.is1_16() || VersionHandler.is1_17()) {
			ArrayList<?> list = (ArrayList<?>) c.getMethod("getSiblings").invoke(o, new Object[0]);
			for (Object component : list) {
				ArrayList<?> innerList = (ArrayList<?>) c.getMethod("getSiblings").invoke(component, new Object[0]);
				if (innerList.size() > 0) {
					splitComponents(finalList, component, c);
				} else {
					finalList.add(component);
				}
			}
		}
		else {
			ArrayList<?> list = (ArrayList<?>) c.getMethod("b").invoke(o, new Object[0]);
			for (Object component : list) {
				ArrayList<?> innerList = (ArrayList<?>) c.getMethod("b").invoke(component, new Object[0]);
				if (innerList.size() > 0) {
					splitComponents(finalList, component, c);
				} else {
					finalList.add(component);
				}
			}
		}
	}

	/**
     * Formats a string with both Spigot legacy colors codes and Spigot and
     * VentureChat hex color codes.
     *
     * @param string to format.
     * @return {@link String}
     */
	public static String FormatStringColor(String string) {
		String allFormated = string;
		allFormated = LEGACY_CHAT_COLOR_DIGITS_PATTERN.matcher(allFormated).replaceAll("\u00A7$1");

		allFormated = allFormated.replaceAll("&[x]", BUKKIT_COLOR_CODE_PREFIX + "x");
		allFormated = allFormated.replaceAll("&[aA]", BUKKIT_COLOR_CODE_PREFIX + "a");
		allFormated = allFormated.replaceAll("&[bB]", BUKKIT_COLOR_CODE_PREFIX + "b");
		allFormated = allFormated.replaceAll("&[cC]", BUKKIT_COLOR_CODE_PREFIX + "c");
		allFormated = allFormated.replaceAll("&[dD]", BUKKIT_COLOR_CODE_PREFIX + "d");
		allFormated = allFormated.replaceAll("&[eE]", BUKKIT_COLOR_CODE_PREFIX + "e");
		allFormated = allFormated.replaceAll("&[fF]", BUKKIT_COLOR_CODE_PREFIX + "f");

		allFormated = allFormated.replaceAll("%", "\\%");

		allFormated = convertHexColorCodeStringToBukkitColorCodeString(allFormated);
		return allFormated;
	}

	/**
     * Formats a string with only legacy Spigot color codes &[0-9a-f]. Does not
     * format the legacy color codes that make up a Spigot hex color code.
     *
     * @param string to format.
     * @return {@link String}
     */
	public static String FormatStringLegacyColor(String string) {
		String allFormated = string;

		allFormated = LEGACY_CHAT_COLOR_PATTERN.matcher(allFormated).replaceAll("\u00A7$13");
		allFormated = allFormated.replaceAll(BUKKIT_COLOR_CODE_PREFIX + "[A]", BUKKIT_COLOR_CODE_PREFIX + "a");
		allFormated = allFormated.replaceAll(BUKKIT_COLOR_CODE_PREFIX + "[B]", BUKKIT_COLOR_CODE_PREFIX + "b");
		allFormated = allFormated.replaceAll(BUKKIT_COLOR_CODE_PREFIX + "[C]", BUKKIT_COLOR_CODE_PREFIX + "c");
		allFormated = allFormated.replaceAll(BUKKIT_COLOR_CODE_PREFIX + "[D]", BUKKIT_COLOR_CODE_PREFIX + "d");
		allFormated = allFormated.replaceAll(BUKKIT_COLOR_CODE_PREFIX + "[E]", BUKKIT_COLOR_CODE_PREFIX + "e");
		allFormated = allFormated.replaceAll(BUKKIT_COLOR_CODE_PREFIX + "[F]", BUKKIT_COLOR_CODE_PREFIX + "f");

		allFormated = allFormated.replaceAll("%", "\\%");
		return allFormated;
	}

	/**
     * Formats a string with Spigot formatting codes.
     *
     * @param string to format.
     * @return {@link String}
     */
	public static String FormatString(String string) {
		String allFormated = string;
		allFormated = allFormated.replaceAll("&[kK]", BUKKIT_COLOR_CODE_PREFIX + "k");
		allFormated = allFormated.replaceAll("&[lL]", BUKKIT_COLOR_CODE_PREFIX + "l");
		allFormated = allFormated.replaceAll("&[mM]", BUKKIT_COLOR_CODE_PREFIX + "m");
		allFormated = allFormated.replaceAll("&[nN]", BUKKIT_COLOR_CODE_PREFIX + "n");
		allFormated = allFormated.replaceAll("&[oO]", BUKKIT_COLOR_CODE_PREFIX + "o");
		allFormated = allFormated.replaceAll("&[rR]", BUKKIT_COLOR_CODE_PREFIX + "r");

		allFormated = allFormated.replaceAll("%", "\\%");
		return allFormated;
	}

	/**
     * Formats a string with Spigot legacy colors codes, Spigot and VentureChat hex
     * color codes, and Spigot formatting codes.
     *
     * @param string to format.
     * @return {@link String}
     */
	public static String FormatStringAll(String string) {
		String allFormated = Format.FormatString(string);
		allFormated = Format.FormatStringColor(allFormated);
		return allFormated;
	}

	public static String applyNexoGlyphPlaceholders(Player player, String string) {
		if (string == null || string.isEmpty()) {
			return string;
		}
		String result = string;
		if (player != null && NEXO_PLACEHOLDER_PATTERN.matcher(result).find()) {
			result = PlaceholderAPI.setPlaceholders(player, result);
		}
		result = resolveNexoMiniMessageTags(result);
		return result;
	}

	// Resolves Nexo's `<glyph:name>` MiniMessage tags (and any tag Nexo has
	// registered with the global MiniMessage instance) into the actual Unicode
	// glyph character. Only fires when the string actually contains a
	// {@code <glyph:} substring so plain text is untouched and other plugins'
	// legacy `<...>` content is left alone.
	private static String resolveNexoMiniMessageTags(String string) {
		if (string == null || string.isEmpty() || !string.contains("<glyph:")) {
			return string;
		}
		try {
			Component parsed = MiniMessage.miniMessage().deserialize(string);
			return LegacyComponentSerializer.legacySection().serialize(parsed);
		} catch (Throwable ignored) {
			return string;
		}
	}

	public static String applyAllPlaceholders(Player player, String string) {
		if (player == null || string == null || string.isEmpty()) {
			return string;
		}
		String resolved = PlaceholderAPI.setBracketPlaceholders(player, string);
		return applyNexoGlyphPlaceholders(player, resolved);
	}

	public static String FilterChat(String msg) {
		int t = 0;
		List<String> filters = getInstance().getConfig().getStringList("filters");
		for (String s : filters) {
			t = 0;
			String[] pparse = new String[2];
			pparse[0] = " ";
			pparse[1] = " ";
			StringTokenizer st = new StringTokenizer(s, ",");
			while (st.hasMoreTokens()) {
				if (t < 2) {
					pparse[t++] = st.nextToken();
				}
			}
			// (?i) = case insensitive
			msg = msg.replaceAll("(?i)" + pparse[0], pparse[1]);
		}
		return msg;
	}

	public static boolean isValidColor(String color) {
		Boolean bFound = false;
		for (ChatColor bkColors : ChatColor.values()) {
			if (color.equalsIgnoreCase(bkColors.name())) {
				bFound = true;
			}
		}
		return bFound;
	}

	/**
     * Validates a hex color code.
     *
     * @param color to validate.
     * @return true if color code is valid, false otherwise.
     */
	public static boolean isValidHexColor(String color) {
		Pattern pattern = Pattern.compile("(^&?#[0-9a-fA-F]{6}\\b)");
		Matcher matcher = pattern.matcher(color);
		return matcher.find();
	}

	/**
     * Convert a single hex color code to a single Bukkit hex color code.
     *
     * @param color to convert.
     * @return {@link String}
     */
	public static String convertHexColorCodeToBukkitColorCode(String color) {
		color = color.replace("&", "");
		StringBuilder bukkitColorCode = new StringBuilder(BUKKIT_COLOR_CODE_PREFIX + BUKKIT_HEX_COLOR_CODE_PREFIX);
		for (int a = 1; a < color.length(); a++) {
			bukkitColorCode.append(BUKKIT_COLOR_CODE_PREFIX + color.charAt(a));
		}
		return bukkitColorCode.toString().toLowerCase();
	}

	/**
     * Convert an entire String of hex color codes to Bukkit hex color codes.
     *
     * @param string to convert.
     * @return {@link String}
     */
	public static String convertHexColorCodeStringToBukkitColorCodeString(String string) {
		Pattern pattern = Pattern.compile("(&?#[0-9a-fA-F]{6})");
		Matcher matcher = pattern.matcher(string);
		while (matcher.find()) {
			int indexStart = matcher.start();
			int indexEnd = matcher.end();
			String hexColor = string.substring(indexStart, indexEnd);
			String bukkitColor = convertHexColorCodeToBukkitColorCode(hexColor);
			string = string.replaceAll(hexColor, bukkitColor);
			matcher.reset(string);
		}
		return string;
	}

	public static String escapeAllRegex(String input) {
		return input.replace("[", "\\[").replace("]", "\\]").replace("{", "\\{").replace("}", "\\}").replace("(", "\\(")
				.replace(")", "\\)").replace("|", "\\|").replace("+", "\\+").replace("*", "\\*");
	}

	public static String underlineURLs() {
		final boolean configValue = getInstance().getConfig().getBoolean("underlineurls", true);
		if (VersionHandler.isAtLeast_1_20_4()) {
			return String.valueOf(configValue);
		} else {
			return "\"" + configValue + "\"";
		}
	}
	
	public static String parseTimeStringFromMillis(long millis) {
		String timeString = "";
		if(millis >= Format.MILLISECONDS_PER_DAY) {
			long numberOfDays = millis / Format.MILLISECONDS_PER_DAY;
			millis -= Format.MILLISECONDS_PER_DAY * numberOfDays;
			
			String units = LocalizedMessage.UNITS_DAY_PLURAL.toString();
			if (numberOfDays == 1) {
				units = LocalizedMessage.UNITS_DAY_SINGULAR.toString();
			}
			timeString += numberOfDays + " " + units + " ";
		}
		
		if(millis >= Format.MILLISECONDS_PER_HOUR) {
			long numberOfHours = millis / Format.MILLISECONDS_PER_HOUR;
			millis -= Format.MILLISECONDS_PER_HOUR * numberOfHours;

			String units = LocalizedMessage.UNITS_HOUR_PLURAL.toString();
			if (numberOfHours == 1) {
				units = LocalizedMessage.UNITS_HOUR_SINGULAR.toString();
			}
			timeString += numberOfHours + " " + units + " ";
		}
		
		if(millis >= Format.MILLISECONDS_PER_MINUTE) {
			long numberOfMinutes = millis / Format.MILLISECONDS_PER_MINUTE;
			millis -= Format.MILLISECONDS_PER_MINUTE * numberOfMinutes;

			String units = LocalizedMessage.UNITS_MINUTE_PLURAL.toString();
			if (numberOfMinutes == 1) {
				units = LocalizedMessage.UNITS_MINUTE_SINGULAR.toString();
			}
			timeString += numberOfMinutes + " " + units + " ";
		}
		
		if(millis >= Format.MILLISECONDS_PER_SECOND) {
			long numberOfSeconds = millis / Format.MILLISECONDS_PER_SECOND;
			millis -= Format.MILLISECONDS_PER_SECOND * numberOfSeconds;

			String units = LocalizedMessage.UNITS_SECOND_PLURAL.toString();
			if (numberOfSeconds == 1) {
				units = LocalizedMessage.UNITS_SECOND_SINGULAR.toString();
			}
			timeString += numberOfSeconds + " " + units;
		}
		return timeString.trim();
	}
	
	public static long parseTimeStringToMillis(String timeInput) {
		long millis = 0L;
		timeInput = timeInput.toLowerCase();
		char validChars[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'd', 'h', 'm', 's' };
		if(containsInvalidChars(validChars, timeInput)) {
			return -1;
		}
		
		long countDayTokens = timeInput.chars().filter(ch -> ch == 'd').count();
		long countHourTokens = timeInput.chars().filter(ch -> ch == 'h').count();
		long countMinuteTokens = timeInput.chars().filter(ch -> ch == 'm').count();
		long countSecondTokens = timeInput.chars().filter(ch -> ch == 's').count();
		if(countDayTokens > 1 || countHourTokens > 1 || countMinuteTokens > 1 || countSecondTokens > 1) {
			return -1;
		}
		
		int indexOfSecondToken = timeInput.indexOf("s");
		int indexOfMinuteToken = timeInput.indexOf("m");
		int indexOfHourToken = timeInput.indexOf("h");
		int indexOfDayToken = timeInput.indexOf("d");
		if(indexOfDayToken != -1) {
			if((indexOfHourToken != -1 && indexOfHourToken < indexOfDayToken) || (indexOfMinuteToken != -1 && indexOfMinuteToken < indexOfDayToken) || (indexOfSecondToken != -1 && indexOfSecondToken < indexOfDayToken)) {
				return -1;
			}
		}
		if(indexOfHourToken != -1) {
			if((indexOfMinuteToken != -1 && indexOfMinuteToken < indexOfHourToken) || (indexOfSecondToken != -1 && indexOfSecondToken < indexOfHourToken)) {
				return -1;
			}
		}
		if(indexOfMinuteToken != -1) {
			if((indexOfSecondToken != -1 && indexOfSecondToken < indexOfMinuteToken)) {
				return -1;
			}
		}
		
		if(indexOfDayToken != -1) {
			int numberOfDays = Integer.parseInt(timeInput.substring(0, indexOfDayToken));
			timeInput = timeInput.substring(indexOfDayToken + 1);
			millis += MILLISECONDS_PER_DAY * numberOfDays;
		}
		if(timeInput.length() > 0) {
			indexOfHourToken = timeInput.indexOf("h");
			if(indexOfHourToken != -1) {
				int numberOfHours = Integer.parseInt(timeInput.substring(0, indexOfHourToken));
				timeInput = timeInput.substring(indexOfHourToken + 1);
				millis += MILLISECONDS_PER_HOUR * numberOfHours;
			}
		}
		if(timeInput.length() > 0) {
			indexOfMinuteToken = timeInput.indexOf("m");
			if(indexOfMinuteToken != -1) {
				int numberOfMinutes = Integer.parseInt(timeInput.substring(0, indexOfMinuteToken));
				timeInput = timeInput.substring(indexOfMinuteToken + 1);
				millis += MILLISECONDS_PER_MINUTE * numberOfMinutes;
			}
		}
		if(timeInput.length() > 0) {
			indexOfSecondToken = timeInput.indexOf("s");
			if(indexOfSecondToken != -1) {
				int numberOfSeconds = Integer.parseInt(timeInput.substring(0, indexOfSecondToken));
				timeInput = timeInput.substring(indexOfSecondToken + 1);
				millis += MILLISECONDS_PER_SECOND * numberOfSeconds;
			}
		}
		return millis;
	}
	
	private static boolean containsInvalidChars(char[] validChars, String validate) {
		for(char c : validate.toCharArray()) {
			boolean isValidChar = false;
			for(char v : validChars) {
				if(c == v) {
					isValidChar = true;
				}
			}
			if(!isValidChar) {
				return true;
			}
		}
		return false;
	}
	
	public static void broadcastToServer(String message) {
		for(MineverseChatPlayer mcp : MineverseChatAPI.getOnlineMineverseChatPlayers()) {
			mcp.getPlayer().sendMessage(message);
		}
	}
	
	public static void playMessageSound(MineverseChatPlayer mcp) {
		if (mcp == null) {
			return;
		}
		Player player = mcp.getPlayer();
		if (player == null) {
			return;
		}
		String soundName = getInstance().getConfig().getString("message_sound", DEFAULT_MESSAGE_SOUND);
		if (soundName.equalsIgnoreCase("None")) {
			return;
		}
		final Sound messageSound;
		try {
			messageSound = getSound(soundName);
		} catch (final Exception e) {
			if (MineverseChat.getInstance().getConfig().getString("loglevel", "info").equals("debug")) {
				Bukkit.getConsoleSender().sendMessage(Format.FormatStringAll("&8[&eVentureChat&8]&c - Error playing sound, defaulting to none"));
			}
			return;
		}
		if (messageSound == null) {
			return;
		}
		// On Folia the entity's location + playSound must be accessed on the
		// region thread that owns the player, otherwise the call throws.
		SchedulerUtil.runForEntity(MineverseChat.getInstance(), player, () -> {
			try {
				player.playSound(player.getLocation(), messageSound, 1, 0);
			} catch (final Exception e) {
				if (MineverseChat.getInstance().getConfig().getString("loglevel", "info").equals("debug")) {
					Bukkit.getConsoleSender().sendMessage(Format.FormatStringAll("&8[&eVentureChat&8]&c - Error playing sound, defaulting to none"));
				}
			}
		});
	}
	
	private static Sound getSound(String soundName) {
		Sound sound = resolveSound(soundName);
		if (sound != null) {
			return sound;
		}
		Bukkit.getConsoleSender().sendMessage(Format.FormatStringAll("&8[&eVentureChat&8]&c - Message sound invalid!"));
		return getDefaultMessageSound();
	}

	private static Sound getDefaultMessageSound() {
		Sound sound;
		if (VersionHandler.is1_7() || VersionHandler.is1_8()) {
			sound = resolveSound(DEFAULT_LEGACY_MESSAGE_SOUND);
		} else {
			sound = resolveSound(DEFAULT_MESSAGE_SOUND);
		}
		return sound;
	}

	private static Sound resolveSound(String soundName) {
		if (soundName == null || soundName.isEmpty()) {
			return null;
		}
		NamespacedKey key = parseSoundKey(soundName);
		if (key != null) {
			Sound sound = Registry.SOUNDS.get(key);
			if (sound != null) {
				return sound;
			}
		}
		return null;
	}

	private static NamespacedKey parseSoundKey(String soundName) {
		String trimmed = soundName.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		if (trimmed.contains(":")) {
			return NamespacedKey.fromString(trimmed.toLowerCase());
		}
		// Enum-style names such as ENTITY_PLAYER_LEVELUP map to entity.player.levelup
		String normalized = trimmed.toLowerCase().replace('_', '.');
		return NamespacedKey.minecraft(normalized);
	}
	
	public static String stripColor(String message) {
		return message.replaceAll("(\u00A7([a-z0-9]))", "");
	}
}
