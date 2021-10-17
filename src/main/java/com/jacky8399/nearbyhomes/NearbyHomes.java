package com.jacky8399.nearbyhomes;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.UserMap;
import com.google.common.collect.Maps;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public final class NearbyHomes extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        getCommand("nearbyhomes").setExecutor(new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                int radius = 50;
                Location location = null;
                if (args.length >= 1) {
                    try {
                        radius = Integer.parseUnsignedInt(args[0]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "Malformed radius: " + args[0]);
                        return true;
                    }
                }
                if (args.length >= 4) {
                    try {
                        int x, y, z;
                        x = Integer.parseInt(args[1]);
                        y = Integer.parseInt(args[2]);
                        z = Integer.parseInt(args[3]);
                        World world = args.length >= 5 ? Bukkit.getWorld(args[5]) : Bukkit.getWorlds().get(0);
                        if (world == null) {
                            sender.sendMessage(ChatColor.RED + "Invalid world: " + args[5]);
                            return true;
                        }
                        location = new Location(world, x, y, z);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "Malformed location: " + String.format("%s %s %s", args[1], args[2], args[3]));
                        return true;
                    }
                }

                if (location == null) {
                    if (sender instanceof Player) {
                        location = ((Player) sender).getLocation();
                    } else {
                        sender.sendMessage(ChatColor.RED + "World and coordinates must be specified when running from console.");
                        return false;
                    }
                }

                Essentials ess = ((Essentials) Bukkit.getPluginManager().getPlugin("Essentials"));
                Location finalLocation = location;
                int finalRadius = radius;
                long start = System.currentTimeMillis();
                Bukkit.getScheduler().runTaskAsynchronously(NearbyHomes.this, () -> {
                    UserMap userMap = ess.getUserMap();
                    sender.sendMessage(ChatColor.GREEN + "Looking up homes of " + userMap.getUniqueUsers() + " players...");
                    List<Map.Entry<Location, String>> nearbyHomes = userMap.getAllUniqueUsers().stream()
                            .map(userMap::getUser)
                            .filter(Objects::nonNull)
                            .flatMap(user -> user.getHomes().stream()
                                    .map(homeName -> {
                                        try {
                                            Location loc = user.getHome(homeName);
                                            if (loc == null) {
                                                return null; // user homeless apparently
                                            }
                                            if (loc.getWorld() != finalLocation.getWorld() || loc.distance(finalLocation) > finalRadius) {
                                                return null;
                                            }
                                            return Maps.immutableEntry(loc, user.getLastAccountName() + ":" + homeName);
                                        } catch (Exception e) {
                                            getLogger().warning("An error occurred while loading home " + user.getLastAccountName() + ":" + homeName + ", skipping");
                                            e.printStackTrace();
                                            return null;
                                        }
                                    })
                                    .filter(Objects::nonNull)
                            )
                            .sorted(Map.Entry.comparingByKey(Comparator.comparingDouble(loc -> loc.distance(finalLocation))))
                            .collect(Collectors.toList());

                    long end = System.currentTimeMillis();

                    sender.sendMessage(ChatColor.GREEN + "Nearby homes in a " + finalRadius + " block radius: (Click on the coords to teleport)");
                    nearbyHomes.forEach(entry -> {
                        Location loc = entry.getKey();
                        String userAndHomeName = entry.getValue();
                        String[] split = userAndHomeName.split(":");
                        String userName = split[0], homeName = split[1];
                        sender.spigot().sendMessage(new ComponentBuilder(String.format("%.1f %.1f %.1f", loc.getX(), loc.getY(), loc.getZ()))
                                .color(ChatColor.YELLOW)
                                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        String.format("/tppos %.0f %.0f %.0f %.0f %.0f %s",
                                                loc.getX(), loc.getY(), loc.getZ(),
                                                loc.getYaw(), loc.getPitch(), loc.getWorld().getName()
                                        ))
                                )
                                .append(String.format(" (%.2f blocks away)", loc.distance(finalLocation)))
                                .color(ChatColor.AQUA)
                                .append(" - " + homeName + " owned by " + userName)
                                .color(ChatColor.AQUA)
                                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/home " + userAndHomeName))
                                .create()
                        );
                    });
                    if (nearbyHomes.size() == 0)
                        sender.sendMessage(ChatColor.YELLOW + "No nearby homes found!");
                    sender.sendMessage(ChatColor.GRAY + "Going through " + userMap.getUniqueUsers() + " players took " + (end - start) + " ms");

                });

                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                if (args.length == 1)
                    return Collections.singletonList("50"); // default value for radius
                if (!(sender instanceof Player))
                    return Collections.emptyList();
                Location location = ((Player) sender).getLocation();
                switch (args.length) {
                    case 2:
                        return Collections.singletonList(Integer.toString(location.getBlockX()));
                    case 3:
                        return Collections.singletonList(Integer.toString(location.getBlockY()));
                    case 4:
                        return Collections.singletonList(Integer.toString(location.getBlockZ()));
                }
                return null;
            }
        });
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
