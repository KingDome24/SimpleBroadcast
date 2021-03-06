/*
 * This class was originally created by BtoBastian.
 * See the following two links for further information:
 * - https://github.com/BtoBastian
 * - https://github.com/BtoBastian/EpicSpleef/blob/master/src/main/java/de/oppermann/bastian/spleef/util/CraftBukkitUtil.java
 */
package net.simplebroadcast.broadcasts;

import java.lang.reflect.Method;

import net.simplebroadcast.Main;
import net.simplebroadcast.methods.Methods;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class JsonMessage {

	private static String VERSION;
	private static boolean nmsFailed = false;
	private static Methods methods = new Methods();
	private static Class<?> CLASS_PACKET;
	private static Class<?> CLASS_CRAFT_PLAYER;
	private static Class<?> CLASS_CHAT_SERIALIZER;
	private static Class<?> CLASS_PACKET_PLAY_OUT_CHAT;
	private static Class<?> CLASS_I_CHAT_BASE_COMPONENT;

	static {
		String path = Bukkit.getServer().getClass().getPackage().getName();
		VERSION = path.substring(path.lastIndexOf(".") + 1, path.length());
		try {
			String serverVersion = Bukkit.getVersion();
			serverVersion = serverVersion.substring(serverVersion.indexOf("(MC: ") + 5, serverVersion.length());
			serverVersion = serverVersion.substring(0, serverVersion.lastIndexOf(")"));
			serverVersion = serverVersion.replace(".", "");
			CLASS_CRAFT_PLAYER = Class.forName("org.bukkit.craftbukkit." + VERSION + ".entity.CraftPlayer");
			CLASS_PACKET_PLAY_OUT_CHAT = Class.forName("net.minecraft.server." + VERSION + ".PacketPlayOutChat");
			CLASS_I_CHAT_BASE_COMPONENT = Class.forName("net.minecraft.server." + VERSION + ".IChatBaseComponent");
			CLASS_PACKET = Class.forName("net.minecraft.server." + VERSION + ".Packet");
			if (Integer.parseInt(serverVersion) < 183) {
				CLASS_CHAT_SERIALIZER = Class.forName("net.minecraft.server." + VERSION + ".ChatSerializer");
			} else {
				for (Class<?> clazz : CLASS_I_CHAT_BASE_COMPONENT.getDeclaredClasses()) {
					if (clazz.getSimpleName().equals("ChatSerializer")) {
						CLASS_CHAT_SERIALIZER = clazz;
						break;
					}
				}
			}
			if (CLASS_CHAT_SERIALIZER == null) {
				nmsFailed = true;
			}
		} catch (ClassNotFoundException e) {
			nmsFailed = true;
		}
	}

	/**
	 * Sends the JSON message to all players.
	 * @param jsonMessage the JSON string
	 */
	public static void sendJSONText(final String jsonMessage) {			
		if (!nmsFailed) {
			Bukkit.getScheduler().runTaskAsynchronously(Main.getPlugin(), new Runnable() {
				@Override
				public void run() {
					try {
						for (Player p : Bukkit.getOnlinePlayers()) {
							if (!Main.ignoredPlayers.contains(methods.getUUID(p.getName()))) {
								Object entityPlayer = CLASS_CRAFT_PLAYER.getMethod("getHandle").invoke(p);
								Object playerConnection = entityPlayer.getClass().getField("playerConnection").get(entityPlayer);					
								Method sendPacketMethod = playerConnection.getClass().getMethod("sendPacket", CLASS_PACKET);
								Object iChatBaseComponent = CLASS_CHAT_SERIALIZER.getMethod("a", String.class).invoke(playerConnection, ChatColor.translateAlternateColorCodes('&', methods.addPlayerVariables(jsonMessage, p)));
								Object packetPlayOutChat = CLASS_PACKET_PLAY_OUT_CHAT.getConstructor(CLASS_I_CHAT_BASE_COMPONENT).newInstance(iChatBaseComponent);
								sendPacketMethod.invoke(playerConnection, packetPlayOutChat);
							}
						}
						if (Main.getPlugin().getConfig().getBoolean("sendmessagestoconsole")) {
							Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', methods.addVariables(jsonMessage.replace("{text:\"", "").replaceAll("\",.*", ""))));
						}
					} catch (Exception e) {
						for (Player p : Bukkit.getOnlinePlayers()) {
							if (!Main.ignoredPlayers.contains(methods.getUUID(p.getName()))) {
								p.sendMessage("§c[SimpleBroadcast] Warning: JSON message isn't formatted correctly or another error occured. Please contact your admin if you see this message.");
							}
						}
						Main.logWarning("Warning: JSON message isn't formatted correctly or another error occured. Please check the JSON messages.");
					}
				}
			});
		}
	}

	/**
	 * Sends the JSON message to a specific player
	 * @param jsonMessage the JSON string
	 * @param p the specific player
	 */
	public static void sendPlayerJSONText(String jsonMessage, Player p) {			
		if (!nmsFailed) {
			try {
				Object entityPlayer = CLASS_CRAFT_PLAYER.getMethod("getHandle").invoke(p);
				Object playerConnection = entityPlayer.getClass().getField("playerConnection").get(entityPlayer);					
				Method sendPacketMethod = playerConnection.getClass().getMethod("sendPacket", CLASS_PACKET);
				Object iChatBaseComponent = CLASS_CHAT_SERIALIZER.getMethod("a", String.class).invoke(playerConnection, ChatColor.translateAlternateColorCodes('&', methods.addPlayerVariables(jsonMessage, p)));
				Object packetPlayOutChat = CLASS_PACKET_PLAY_OUT_CHAT.getConstructor(CLASS_I_CHAT_BASE_COMPONENT).newInstance(iChatBaseComponent);
				sendPacketMethod.invoke(playerConnection, packetPlayOutChat);
			} catch (Exception e) {
				p.sendMessage("§c[SimpleBroadcast] Warning: JSON message isn't formatted correctly or another error occured. Please check the JSON messages.");
				Main.logWarning("Warning: JSON message isn't formatted correctly or another error occured. Please check the JSON messages.");
			}
		}
	}
}