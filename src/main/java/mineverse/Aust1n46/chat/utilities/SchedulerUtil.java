package mineverse.Aust1n46.chat.utilities;

import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Thin wrapper around Bukkit scheduling that transparently uses the correct
 * scheduler depending on whether the server is Folia or classic Paper/Spigot.
 *
 * <p>Folia removes the notion of a single main thread: entities and worlds
 * are owned by region threads, and {@code BukkitScheduler.runTask*} sync
 * variants throw {@link UnsupportedOperationException}. This class routes
 * work to {@code GlobalRegionScheduler}, {@code EntityScheduler} and
 * {@code AsyncScheduler} when running on Folia, and falls back to the
 * classic {@code BukkitScheduler} otherwise.</p>
 */
public final class SchedulerUtil {

	private static final boolean FOLIA;
	static {
		boolean folia;
		try {
			Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
			folia = true;
		} catch (ClassNotFoundException ignored) {
			folia = false;
		}
		FOLIA = folia;
	}

	private SchedulerUtil() {
	}

	public static boolean isFolia() {
		return FOLIA;
	}

	/**
	 * Runs a task on the global region thread (Folia) or the main thread
	 * (Paper/Spigot) as soon as possible.
	 */
	public static void runGlobal(Plugin plugin, Runnable task) {
		if (FOLIA) {
			Bukkit.getGlobalRegionScheduler().execute(plugin, task);
		} else {
			Bukkit.getScheduler().runTask(plugin, task);
		}
	}

	/**
	 * Runs a task on the global region thread (Folia) or the main thread
	 * (Paper/Spigot) after {@code delayTicks} ticks. Folia requires the delay
	 * to be at least one tick.
	 */
	public static void runGlobalLater(Plugin plugin, Runnable task, long delayTicks) {
		long delay = Math.max(delayTicks, 1L);
		if (FOLIA) {
			Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delay);
		} else {
			Bukkit.getScheduler().runTaskLater(plugin, task, delay);
		}
	}

	/**
	 * Runs a repeating task on the global region thread (Folia) or the main
	 * thread (Paper/Spigot). Both delays are in ticks and clamped to at least
	 * one.
	 */
	public static void runGlobalTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
		long delay = Math.max(delayTicks, 1L);
		long period = Math.max(periodTicks, 1L);
		if (FOLIA) {
			Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), delay, period);
		} else {
			Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
		}
	}

	/**
	 * Runs a task on the region thread that owns {@code entity} (Folia) or on
	 * the main thread (Paper/Spigot). Silently drops the task if the entity
	 * is retired on Folia.
	 */
	public static void runForEntity(Plugin plugin, Entity entity, Runnable task) {
		if (entity == null) {
			return;
		}
		if (FOLIA) {
			entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
		} else {
			Bukkit.getScheduler().runTask(plugin, task);
		}
	}

	/**
	 * Runs an async task using {@code AsyncScheduler} on Folia, falling back
	 * to {@code BukkitScheduler.runTaskAsynchronously} otherwise. Both paths
	 * remain valid on classic Paper.
	 */
	public static void runAsync(Plugin plugin, Runnable task) {
		if (FOLIA) {
			Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
		} else {
			Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
		}
	}

	/**
	 * Runs an async task after {@code delayTicks} ticks. On Folia the delay
	 * is converted to milliseconds because {@code AsyncScheduler} does not
	 * use ticks.
	 */
	public static void runAsyncLater(Plugin plugin, Runnable task, long delayTicks) {
		long delay = Math.max(delayTicks, 1L);
		if (FOLIA) {
			Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(), delay * 50L, TimeUnit.MILLISECONDS);
		} else {
			Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
		}
	}

	/**
	 * Runs a repeating async task. Ticks are converted to milliseconds on
	 * Folia.
	 */
	public static void runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
		long delay = Math.max(delayTicks, 1L);
		long period = Math.max(periodTicks, 1L);
		if (FOLIA) {
			Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), delay * 50L, period * 50L,
					TimeUnit.MILLISECONDS);
		} else {
			Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
		}
	}
}
