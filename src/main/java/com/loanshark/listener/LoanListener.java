package com.loanshark.listener;

import com.loanshark.LoanSharkPlugin;
import com.loanshark.model.LoanData;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class LoanListener implements Listener {

    private final LoanSharkPlugin plugin;

    public LoanListener(LoanSharkPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player)) return;
        if (event.getVehicle() instanceof Minecart minecart
                && minecart.getCustomName() != null
                && minecart.getCustomName().contains("大运")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        LoanData loanData = plugin.getLoanManager().getLoanData(player);
        if (loanData.hasAnyLoan()) {
            long interestIntervalMs = plugin.getLoanManager().getInterestIntervalMs();
            int overdueDays = loanData.getOverdueDays(interestIntervalMs);
            int configOverdueDays = plugin.getLoanManager().getOverdueDays();

            if (overdueDays >= configOverdueDays) {
                String msg = plugin.getConfig().getString("messages.overdue_warning",
                        "&c\u26a0 你的贷款已逾期 {days} 个游戏日！请尽快还款！");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        msg.replace("{days}", String.valueOf(overdueDays))));
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getPunishmentManager().cancelPunishment(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        plugin.getPunishmentManager().cancelPunishment(player);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        if (inv == null) return;
        if (!event.getView().getTitle().equals(ChatColor.DARK_RED + "" + ChatColor.BOLD + "高利贷 - 利滚利")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        String displayName = clicked.getItemMeta().getDisplayName();
        String stripped = ChatColor.stripColor(displayName);

        if (displayName.contains("关闭") || clicked.getType() == Material.BARRIER && !displayName.contains("警告")) {
            player.closeInventory();
            return;
        }

        if (displayName.contains("借款")) {
            handleBorrow(player, stripped);
            return;
        }

        if (displayName.contains("偿还")) {
            handleRepay(player, stripped);
            return;
        }
    }

    private void handleBorrow(Player player, String displayName) {
        String numStr = displayName.replaceAll("[^0-9]", "");
        if (numStr.isEmpty()) {
            player.sendMessage(ChatColor.RED + "无效的借款金额！");
            return;
        }
        try {
            double amount = Double.parseDouble(numStr);
            plugin.getLoanManager().takeActiveLoan(player, amount);
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getLoanGUI().open(player));
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "无效的借款金额！");
        }
    }

    private void handleRepay(Player player, String displayName) {
        LoanData loanData = plugin.getLoanManager().getLoanData(player);
        double totalLoan = loanData.getTotalLoan();

        if (displayName.contains("全部")) {
            if (totalLoan <= 0) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.no_loan", "&7你当前没有贷款。")));
                return;
            }
            double balance = plugin.getLoanManager().getBalance(player);
            double actualRepay = Math.min(totalLoan, balance);
            if (actualRepay <= 0) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.insufficient_balance_repay", "&c你没有足够的钱来还款！")));
                return;
            }
            plugin.getLoanManager().repayActiveLoan(player, actualRepay);
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getLoanGUI().open(player));
            return;
        }

        String numStr = displayName.replaceAll("[^0-9]", "");
        if (numStr.isEmpty()) {
            player.sendMessage(ChatColor.RED + "无效的还款金额！");
            return;
        }
        try {
            double amount = Double.parseDouble(numStr);
            plugin.getLoanManager().repayActiveLoan(player, amount);
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getLoanGUI().open(player));
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "无效的还款金额！");
        }
    }
}
