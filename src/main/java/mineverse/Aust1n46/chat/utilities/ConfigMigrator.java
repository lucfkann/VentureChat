package mineverse.Aust1n46.chat.utilities;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Recursively merges missing keys from the JAR's default config into the
 * user's on-disk config without overwriting any value they have customised.
 *
 * <p>Some top-level sections carry user-defined subkeys (channel names,
 * aliases, JSON format groups, GUI slots…). For those, only the SECTION
 * itself is added when it is missing; existing sections are left untouched
 * so admins can freely rename or remove entries without them being
 * re-added on every startup.</p>
 */
public final class ConfigMigrator {

	// Paths whose subkeys are user-defined and must not be repopulated with
	// the defaults when the section already exists.
	private static final Set<String> SKIP_SUBKEY_MERGE = Set.of(
			"channels",
			"alias",
			"jsonformatting",
			"venturegui");

	private ConfigMigrator() {
	}

	/**
	 * Merges missing keys from {@code resourceName} (a YAML file bundled in
	 * the plugin JAR) into {@code onDiskFile}. Returns the number of keys
	 * that were added. When at least one key is added the merged
	 * configuration is written back to disk and the plugin's live
	 * {@code getConfig()} is reloaded.
	 */
	public static int migrate(JavaPlugin plugin, String resourceName, File onDiskFile) {
		if (!onDiskFile.exists()) {
			return 0;
		}
		final InputStream defaultStream = plugin.getResource(resourceName);
		if (defaultStream == null) {
			return 0;
		}
		final YamlConfiguration defaults;
		try (Reader reader = new InputStreamReader(defaultStream, StandardCharsets.UTF_8)) {
			defaults = YamlConfiguration.loadConfiguration(reader);
		} catch (IOException e) {
			plugin.getLogger().warning("[ConfigMigrator] Could not read bundled defaults: " + e.getMessage());
			return 0;
		}
		final YamlConfiguration user = YamlConfiguration.loadConfiguration(onDiskFile);

		int added = mergeMissing(defaults, user, "");
		if (added > 0) {
			try {
				user.save(onDiskFile);
				plugin.reloadConfig();
				Bukkit.getConsoleSender().sendMessage("§8[§eVentureChat§8]§e - Added " + added
						+ " missing config key" + (added == 1 ? "" : "s") + " to config.yml (existing values kept).");
			} catch (IOException e) {
				plugin.getLogger().warning("[ConfigMigrator] Could not save merged config.yml: " + e.getMessage());
			}
		}
		return added;
	}

	private static int mergeMissing(ConfigurationSection defaults, ConfigurationSection user, String path) {
		int added = 0;
		for (String key : defaults.getKeys(false)) {
			String fullPath = path.isEmpty() ? key : path + "." + key;
			Object defaultValue = defaults.get(key);

			if (defaultValue instanceof ConfigurationSection defaultSection) {
				ConfigurationSection userSection = user.getConfigurationSection(key);
				if (userSection == null) {
					// Whole section is missing on the user side — copy it in one shot.
					copySection(defaultSection, user.createSection(key));
					added += countKeys(defaultSection);
					continue;
				}
				// Section is present. Recurse only when the user is expected to
				// have the same subkeys as us (config settings), not user-defined
				// entries (channels, aliases, format groups, GUI slots).
				if (!SKIP_SUBKEY_MERGE.contains(fullPath)) {
					added += mergeMissing(defaultSection, userSection, fullPath);
				}
			} else if (!user.contains(key, true)) {
				user.set(key, defaultValue);
				added++;
			}
		}
		return added;
	}

	private static void copySection(ConfigurationSection from, ConfigurationSection to) {
		for (String key : from.getKeys(false)) {
			Object value = from.get(key);
			if (value instanceof ConfigurationSection nested) {
				copySection(nested, to.createSection(key));
			} else {
				to.set(key, value);
			}
		}
	}

	private static int countKeys(ConfigurationSection section) {
		int count = 0;
		for (String key : section.getKeys(false)) {
			Object value = section.get(key);
			if (value instanceof ConfigurationSection nested) {
				count += countKeys(nested);
			} else {
				count++;
			}
		}
		return count;
	}
}
