package com.loanshark.manager;

import com.loanshark.LoanSharkPlugin;
import com.loanshark.model.LoanData;
import org.bukkit.*;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import io.papermc.paper.datacomponent.DataComponentTypes;

import java.util.*;

public class PunishmentManager {

    private final LoanSharkPlugin plugin;
    private final Set<UUID> playersInPunishment = new HashSet<>();
    private final Map<UUID, Minecart> activeMinecarts = new HashMap<>();

    private int countdownSeconds;
    private double punishmentDistance;
    private double minecartSpeed;
    private String soundName;
    private float soundVolume;
    private float soundPitch;

    public PunishmentManager(LoanSharkPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        this.countdownSeconds = plugin.getConfig().getInt("settings.countdown_seconds", 5);
        this.punishmentDistance = plugin.getConfig().getDouble("settings.punishment_distance", 10);
        this.minecartSpeed = plugin.getConfig().getDouble("settings.minecart_speed", 2.5);
        this.soundName = plugin.getConfig().getString("sound.punishment_sound", "loanshark.punishment");
        this.soundVolume = (float) plugin.getConfig().getDouble("sound.volume", 1.0);
        this.soundPitch = (float) plugin.getConfig().getDouble("sound.pitch", 1.0);
    }

    public void triggerPunishment(Player player, LoanData loanData) {
        if (playersInPunishment.contains(player.getUniqueId())) return;

        long cooldownMs = plugin.getConfig().getLong("settings.punishment_cooldown_seconds", 1200) * 1000L;
        if (System.currentTimeMillis() - loanData.getLastPunishmentTime() < cooldownMs) return;

        playersInPunishment.add(player.getUniqueId());
        startPunishmentSequence(player, loanData);
    }

    private void startPunishmentSequence(Player player, LoanData loanData) {
        World world = player.getWorld();
        Location spawnLoc = getLocationInFront(player, punishmentDistance);
        Minecart minecart = world.spawn(spawnLoc, Minecart.class);
        minecart.setCustomNameVisible(true);
        minecart.setCustomName(ChatColor.RED + "大运");
        minecart.setInvulnerable(true);
        minecart.setPersistent(true);

        activeMinecarts.put(player.getUniqueId(), minecart);

        String announceMsg = plugin.getConfig().getString("messages.punishment_announce",
                "&c&l【高利贷】&r &4{player} &c贷款逾期未还！大运即将来临！");
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                announceMsg.replace("{player}", player.getName())));

        playWarningSound(player, minecart);

        executeCountdown(player, minecart, loanData, countdownSeconds);
    }

    private void executeCountdown(Player player, Minecart minecart, LoanData loanData, int remaining) {
        if (remaining <= 0) {
            executeImpact(player, minecart, loanData);
            return;
        }

        if (!player.isOnline() || player.isDead()) {
            cleanupPunishment(player, minecart);
            return;
        }

        updateMinecartPosition(player, minecart);

        String countdownMsg = plugin.getConfig().getString("messages.punishment_countdown",
                "&c倒计时: &4&l{seconds}&c 秒！");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                countdownMsg.replace("{seconds}", String.valueOf(remaining))));

        new BukkitRunnable() {
            @Override
            public void run() {
                executeCountdown(player, minecart, loanData, remaining - 1);
            }
        }.runTaskLater(plugin, 20L);
    }

    private void executeImpact(Player player, Minecart minecart, LoanData loanData) {
        if (!player.isOnline() || player.isDead()) {
            cleanupPunishment(player, minecart);
            return;
        }

        Vector direction = player.getLocation().toVector()
                .subtract(minecart.getLocation().toVector()).normalize();
        minecart.setVelocity(direction.multiply(minecartSpeed));

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || minecart.isDead()) {
                    cleanupPunishment(player, minecart);
                    cancel();
                    return;
                }

                if (minecart.getLocation().distance(player.getLocation()) < 2.0 || ticks > 100) {
                    player.setHealth(0);
                    World world = player.getWorld();
                    world.strikeLightningEffect(player.getLocation());
                    world.spawnParticle(Particle.EXPLOSION, player.getLocation(), 5);

                    dropDisc(player);

                    plugin.getLoanManager().reduceLoanOnPunishment(loanData);

                    String executedMsg = plugin.getConfig().getString("messages.punishment_executed",
                            "&4&l大运降临！&r &c{player} &4因逾期未还贷款被制裁！贷款减少20%！");
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                            executedMsg.replace("{player}", player.getName())));

                    cleanupPunishment(player, minecart);
                    cancel();
                } else {
                    Vector toPlayer = player.getLocation().toVector()
                            .subtract(minecart.getLocation().toVector()).normalize();
                    minecart.setVelocity(toPlayer.multiply(minecartSpeed * 1.5));
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void updateMinecartPosition(Player player, Minecart minecart) {
        Location newLoc = getLocationInFront(player, punishmentDistance);
        minecart.teleport(newLoc);
    }

    private Location getLocationInFront(Player player, double distance) {
        Location loc = player.getLocation().clone();
        Vector direction = loc.getDirection().normalize();
        loc.add(direction.multiply(distance));
        loc.setY(loc.getY() + 1);
        World world = player.getWorld();
        int highestY = world.getHighestBlockYAt(loc);
        if (loc.getY() < highestY + 1) {
            loc.setY(highestY + 2);
        }
        loc.setPitch(0);
        loc.setYaw(player.getLocation().getYaw());
        return loc;
    }

    private void playWarningSound(Player player, Minecart minecart) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "playsound " + soundName + " master " + player.getName() + " ~ ~ ~ 1 1");
        plugin.getLogger().info("[Sound] dispatched playsound for " + player.getName());

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && !minecart.isDead()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            "stopsound " + player.getName());
                }
            }
        }.runTaskLater(plugin, 200L);
    }

    private void dropDisc(Player player) {
        ItemStack disc = ItemStack.of(Material.MUSIC_DISC_13);
        disc.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        disc.editMeta(meta -> meta.displayName(
                net.kyori.adventure.text.Component.text("大运唱片", net.kyori.adventure.text.format.NamedTextColor.GOLD)));
        player.getWorld().dropItemNaturally(player.getLocation(), disc);
    }

    private void cleanupPunishment(Player player, Minecart minecart) {
        playersInPunishment.remove(player.getUniqueId());
        if (minecart != null && !minecart.isDead()) {
            minecart.remove();
        }
        activeMinecarts.remove(player.getUniqueId());
    }

    public boolean isInPunishment(UUID uuid) {
        return playersInPunishment.contains(uuid);
    }

    public void triggerDayun(Player player) {
        LoanData dummy = new LoanData(player.getUniqueId());
        dummy.setActiveLoanAmount(0);
        triggerPunishment(player, dummy);
    }

    public void cancelPunishment(Player player) {
        UUID uuid = player.getUniqueId();
        playersInPunishment.remove(uuid);
        Minecart minecart = activeMinecarts.remove(uuid);
        if (minecart != null && !minecart.isDead()) {
            minecart.remove();
        }
    }
}
