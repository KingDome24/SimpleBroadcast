package net.simplebroadcast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.simplebroadcast.broadcasts.BossBarBroadcast;
import net.simplebroadcast.broadcasts.ChatBroadcast;
import net.simplebroadcast.events.Events;
import net.simplebroadcast.methods.UpdatingMethods;
import net.simplebroadcast.utils.UUIDFetcher;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcstats.Metrics;
import org.mcstats.Metrics.Graph;

public class Main extends JavaPlugin {

	public static Main plugin;
	public static List<String> ignoredPlayers = new ArrayList<String>();
	public static HashMap<Integer, String> chatMessages = new HashMap<Integer, String>();
	public static HashMap<Integer, String> bossBarMessages = new HashMap<Integer, String>();

	@Override
	public void onDisable() {
		Bukkit.getScheduler().cancelTask(BossBarBroadcast.getBarTask());
		Bukkit.getScheduler().cancelTask(ChatBroadcast.getMessageTask());
	}

	@Override
	public void onEnable() {
		plugin = this;

		/*
		 * Saves all config files and the readme
		 */
		saveDefaultConfig();
		if (!new File(getDataFolder(), "ignore.yml").exists()) {
			saveResource("ignore.yml", false);
		}
		if (!new File(getDataFolder(), "bossbar.yml").exists()) {
			saveResource("bossbar.yml", false);
		}
		saveResource("readme.txt", true);

		/*
		 * Loads all messages and ignored players
		 */
		loadChatMessages();
		loadBossBarMessages();
		loadIgnoredPlayers();

		/*
		 * Checks if the plugin shall be enabled and logs it if not.
		 */
		if (!getConfig().getBoolean("enabled")) {
			logInfo("Messages don't get broadcasted as set in the config.");
			Bukkit.getScheduler().cancelTask(ChatBroadcast.getMessageTask());
			ChatBroadcast.setRunning(3);
		}

		/*
		 * Initializes the main command.
		 */
		getCommand("simplebroadcast").setExecutor(new Commands());

		/*
		 * Connects to mcstats.org and sends data (see list below).
		 * - Prefix
		 * - Suffix
		 * - Update checker
		 * - Message randomizer
		 * - Online players required
		 * - Console messages
		 * - Bossbar
		 * - Reduce health bar
		 */		
		try {
			Metrics metrics = new Metrics(plugin);
			Graph enabledFeatures = metrics.createGraph("Enabled Features");
			if (getConfig().getBoolean("prefix.enabled")) {
				enabledFeatures.addPlotter(new Metrics.Plotter("Prefix") {
					@Override
					public int getValue() {
						return 1;
					}
				});
			}
			if (getConfig().getBoolean("suffix.enabled")) {
				enabledFeatures.addPlotter(new Metrics.Plotter("Suffix") {
					@Override
					public int getValue() {
						return 1;
					}
				});
			}
			if (getConfig().getBoolean("checkforupdates")) {
				enabledFeatures.addPlotter(new Metrics.Plotter("Update checker") {
					@Override
					public int getValue() {
						return 1;
					}
				});
			}
			if (getConfig().getBoolean("randomizemessages")) {
				enabledFeatures.addPlotter(new Metrics.Plotter("Message randomizer") {
					@Override
					public int getValue() {
						return 1;
					}
				});
			}
			if (getConfig().getBoolean("requiresonlineplayers")) {
				enabledFeatures.addPlotter(new Metrics.Plotter("Online players required") {
					@Override
					public int getValue() {
						return 1;
					}
				});
			}
			if (getConfig().getBoolean("sendmessagestoconsole")) {
				enabledFeatures.addPlotter(new Metrics.Plotter("Console messages") {
					@Override
					public int getValue() {
						return 1;
					}
				});
			}
			if (getBossBarConfig().getBoolean("enabled")) {
				enabledFeatures.addPlotter(new Metrics.Plotter("Bossbar") {
					@Override
					public int getValue() {
						return 1;
					}
				});
				if (getBossBarConfig().getBoolean("reducehealthbar")) {
					enabledFeatures.addPlotter(new Metrics.Plotter("Reduce health bar") {
						@Override
						public int getValue() {
							return 1;
						}
					});
				}
			}
			metrics.start();
		} catch (IOException e) {
			logWarning(e.getMessage());
		}

		/*
		 * Checks if any updates are available.
		 * (Asynchronous task)
		 */
		if (getConfig().getBoolean("enabled") && plugin.getConfig().getBoolean("checkforupdates")) {
			Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
				@Override
				public void run() {
					UpdatingMethods updatingMethods = new UpdatingMethods();
					updatingMethods.update();
				}
			});
		}

		/*
		 * Registers the events.
		 */
		if (getConfig().getBoolean("enabled")) {
			getServer().getPluginManager().registerEvents(new Events(), plugin);
		}

		/*
		 * Checks if the boss bar is enabled and BarAPI is installed.
		 */
		if (getBossBarConfig().getBoolean("enabled") && getServer().getPluginManager().isPluginEnabled("BarAPI")) {
			logInfo("BarAPI integration successfully enabled.");
		} else {
			getServer().getScheduler().cancelTask(BossBarBroadcast.getBarTask());
			BossBarBroadcast.setBarRunning(3);
		}

		/*
		 * Starts the chat broadcast task.
		 */
		if (getConfig().getBoolean("enabled")) {
			ChatBroadcast chatBroadcast = new ChatBroadcast();
			if (!plugin.getConfig().getBoolean("requiresonlineplayers")) {
				chatBroadcast.chatBroadcast();
			} else {
				if (Bukkit.getOnlinePlayers().size() >= 1) {
					chatBroadcast.chatBroadcast();
				} else {
					ChatBroadcast.setRunning(0);
				}
			}
		}

		/*
		 * Starts the boss bar broadcast task.
		 */
		if (getConfig().getBoolean("enabled") && getBossBarConfig().getBoolean("enabled") && BossBarBroadcast.getBarRunning() != 0 && BossBarBroadcast.getBarRunning() != 3) {
			BossBarBroadcast bossBarBroadcast = new BossBarBroadcast();
			bossBarBroadcast.barBroadcast();
		}

		/*
		 * Converts the old ignore.yml to the new format if existing.
		 * Converts the user names to UUID's (asynchronous task).
		 */
		final File ignore = new File(getDataFolder(), "ignore.yml");
		File old = new File(getDataFolder(), "ignore.yml.OLD");
		final FileConfiguration cfg_old = YamlConfiguration.loadConfiguration(ignore);
		if (!old.exists() && cfg_old.get("format") == null) {
			ignore.renameTo(old);

			final FileConfiguration cfg_new = YamlConfiguration.loadConfiguration(ignore);
			final List<String> ignorePlayers = cfg_new.getStringList("players");
			final FileConfiguration cfg_converted = YamlConfiguration.loadConfiguration(old);

			try {
				cfg_new.save(ignore);
			} catch (IOException e) {
				logWarning("Couldn't save new ignore.yml.");
			}
			Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
				@Override
				public void run() {
					UUIDFetcher fetcher = new UUIDFetcher(cfg_converted.getStringList("players"));
					Map<String, UUID> response = null;
					try {
						response = fetcher.call();
					} catch (Exception e) {
						logWarning("Couldn't convert ignore.yml.");
					}

					for (UUID uuid : response.values()) {
						ignorePlayers.add(uuid.toString());
					}
					cfg_new.set("players", ignorePlayers);
					cfg_new.options().header("Since v1.7 the plugin uses the UUID of the player. Please DO NOT edit this file!");
					cfg_new.addDefault("format", "uuid");
					cfg_new.options().copyDefaults(true);
					try {
						cfg_new.save(ignore);
					} catch (IOException e) {
						logWarning("Couldn't save new ignore.yml.");
					}
				}
			});
		}
	}

	/**
	 * Gets the instance of the Main class.
	 * @return instance of the Main class.
	 */
	public static Main getPlugin() {
		return plugin;
	}

	/**
	 * Logs the given message with the status "info".
	 * @param message logged message
	 */
	public static void logInfo(String message) {
		Bukkit.getLogger().info("[SimpleBroadcast] " + message);
	}

	/**
	 * Logs the given message with the status "warning".
	 * @param message logged message
	 */
	public static void logWarning(String message) {
		Bukkit.getLogger().warning("[SimpleBroadcast] " + message);
	}

	/**
	 * Loads all messages of the chat broadcast into a HashMap.
	 */
	public static void loadChatMessages() {
		int messageID = 0;
		chatMessages.clear();
		for (String message : plugin.getConfig().getStringList("messages")) {
			chatMessages.put(messageID, message);
			messageID++;
		}
	}

	/**
	 * Loads all messages of the boss bar broadcast into a HashMap.
	 */
	public static void loadBossBarMessages() {
		int messageID = 0;
		bossBarMessages.clear();
		for (String message : getBossBarConfig().getStringList("messages")) {
			bossBarMessages.put(messageID, message);
			messageID++;
		}
	}

	/**
	 * Loads all UUIDs of the players into a StringList
	 */
	public static void loadIgnoredPlayers() {
		ignoredPlayers.clear();
		for (String uuid : getIgnoreConfig().getStringList("players")) {
			ignoredPlayers.add(uuid);
		}
	}

	/**
	 * Gets the boss bar configuration file.
	 * @return boss bar configuration file
	 */
	public static FileConfiguration getBossBarConfig() {
		File bossBar = new File(plugin.getDataFolder(), "bossbar.yml");
		FileConfiguration bossBarConfig = YamlConfiguration.loadConfiguration(bossBar);
		return bossBarConfig;
	}

	/**
	 * Gets the ignore configuration file.
	 * @return ignore configuration file.
	 */
	public static FileConfiguration getIgnoreConfig() {
		File ignore = new File (plugin.getDataFolder(), "ignore.yml");
		FileConfiguration ignoreConfig = YamlConfiguration.loadConfiguration(ignore);
		return ignoreConfig;
	}
}