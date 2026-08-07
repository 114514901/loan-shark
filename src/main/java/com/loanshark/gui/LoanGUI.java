package com.loanshark.gui;

import com.loanshark.LoanSharkPlugin;
import com.loanshark.model.LoanData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class LoanGUI {

    private static final String TITLE = ChatColor.DARK_RED + "" + ChatColor.BOLD + "高利贷 - 利滚利";
    private static final int SIZE = 54;

    private final LoanSharkPlugin plugin;

    public LoanGUI(LoanSharkPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, SIZE, TITLE);
        LoanData loanData = plugin.getLoanManager().getLoanData(player);
        double activeLoan = loanData.getActiveLoanAmount();
        double passiveLoan = loanData.getPassiveLoanAmount();
        double totalLoan = loanData.getTotalLoan();
        double interestRate = plugin.getLoanManager().getInterestRate();
        long interestIntervalMs = plugin.getLoanManager().getInterestIntervalMs();
        int overdueDays = loanData.getOverdueDays(interestIntervalMs);
        long nextInterest = loanData.getNextInterestTime(interestIntervalMs);
        long untilNext = Math.max(0, (nextInterest - System.currentTimeMillis()) / 1000);

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, createGlass(Material.RED_STAINED_GLASS_PANE, " "));
        }

        inv.setItem(4, createInfoItem(player, activeLoan, passiveLoan, interestRate,
                overdueDays, untilNext));

        List<Double> borrowPresets = plugin.getConfig().getDoubleList("settings.borrow_presets");
        if (borrowPresets.isEmpty()) {
            borrowPresets = List.of(100.0, 500.0, 1000.0, 5000.0);
        }
        int[] borrowSlots = {19, 20, 21, 22};
        for (int i = 0; i < Math.min(borrowPresets.size(), borrowSlots.length); i++) {
            double amount = borrowPresets.get(i);
            inv.setItem(borrowSlots[i], createBorrowItem(amount));
        }

        List<Double> repayPresets = plugin.getConfig().getDoubleList("settings.repay_presets");
        if (repayPresets.isEmpty()) {
            repayPresets = List.of(100.0, 500.0, 1000.0, 0.0);
        }
        int[] repaySlots = {28, 29, 30, 31};
        for (int i = 0; i < Math.min(repayPresets.size(), repaySlots.length); i++) {
            double amount = repayPresets.get(i);
            inv.setItem(repaySlots[i], createRepayItem(amount, totalLoan));
        }

        Material btnColor = (totalLoan > 0) ? Material.GREEN_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, createGlass(btnColor, " "));
        }
        for (int i = 0; i < 6; i++) {
            int rowStart = i * 9;
            if (inv.getItem(rowStart) == null) inv.setItem(rowStart, createGlass(Material.GRAY_STAINED_GLASS_PANE, " "));
            if (inv.getItem(rowStart + 8) == null) inv.setItem(rowStart + 8, createGlass(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        inv.setItem(49, createCloseButton());

        inv.setItem(23, createWarningItem());
        inv.setItem(32, createWarningItem());

        player.openInventory(inv);
    }

    private ItemStack createGlass(Material material, String name) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RESET + name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem(Player player, double activeLoan, double passiveLoan,
                                     double interestRate, int overdueDays, long untilNextInterest) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + player.getName() + " 的贷款信息");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━");
        lore.add(ChatColor.YELLOW + "主动贷款: " + ChatColor.RED + String.format("%.0f", activeLoan));
        lore.add(ChatColor.YELLOW + "被动贷款: " + ChatColor.RED + String.format("%.0f", passiveLoan));
        lore.add(ChatColor.YELLOW + "利率: " + ChatColor.RED + String.format("%.0f", interestRate * 100) +
                "%" + ChatColor.GRAY + " /游戏日 (利滚利)");
        lore.add(ChatColor.YELLOW + "逾期天数: " + ChatColor.RED + overdueDays + " / " +
                plugin.getLoanManager().getOverdueDays());
        if (untilNextInterest > 0) {
            long min = untilNextInterest / 60;
            long sec = untilNextInterest % 60;
            lore.add(ChatColor.YELLOW + "下次利息: " + ChatColor.GRAY + min + "分" + sec + "秒后");
        }
        if (activeLoan > 0 || passiveLoan > 0) {
            lore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━");
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "⚡ 警告: 逾期" + plugin.getLoanManager().getOverdueDays() +
                    "个游戏日将触发大运惩罚！");
            lore.add(ChatColor.YELLOW + "余额: " + ChatColor.GREEN +
                    String.format("%.0f", plugin.getLoanManager().getBalance(player)));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBorrowItem(double amount) {
        ItemStack item = new ItemStack(Material.EMERALD, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "借款 " + ChatColor.GOLD + "$" + String.format("%.0f", amount));
        List<String> lore = new ArrayList<>();
        double interestRate = plugin.getLoanManager().getInterestRate();
        lore.add(ChatColor.GRAY + "日利率: " + ChatColor.RED + String.format("%.0f", interestRate * 100) + "% (利滚利)");
        lore.add(ChatColor.GRAY + "每日利息将计入本金继续计算利息");
        lore.add(ChatColor.RED + "⚠ 逾期将被大运制裁!");
        lore.add(ChatColor.GRAY + "点击借款");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRepayItem(double amount, double totalLoan) {
        boolean isRepayAll = amount <= 0;
        Material mat = isRepayAll ? Material.GOLD_BLOCK : Material.GOLD_INGOT;
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (isRepayAll) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "偿还全部 " +
                    ChatColor.YELLOW + "$" + String.format("%.0f", totalLoan));
        } else {
            meta.setDisplayName(ChatColor.YELLOW + "偿还 " + ChatColor.GOLD + "$" + String.format("%.0f", amount));
        }
        List<String> lore = new ArrayList<>();
        if (totalLoan <= 0) {
            lore.add(ChatColor.GRAY + "你当前没有贷款");
        } else {
            lore.add(ChatColor.GRAY + "当前贷款: " + ChatColor.RED + String.format("%.0f", totalLoan));
            lore.add(ChatColor.GRAY + "点击偿还");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createWarningItem() {
        ItemStack item = new ItemStack(Material.BARRIER, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "⚠ 高利贷警告 ⚠");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.RED + "利滚利，利息按日计算，");
        lore.add(ChatColor.RED + "利息将计入本金继续计算利息！");
        lore.add(ChatColor.RED + "逾期" + plugin.getLoanManager().getOverdueDays() + "个游戏日将触发大运制裁！");
        lore.add(ChatColor.DARK_RED + "大运将撞死你并减少20%贷款！");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "关闭");
        item.setItemMeta(meta);
        return item;
    }
}
