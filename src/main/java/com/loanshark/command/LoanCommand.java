package com.loanshark.command;

import com.loanshark.LoanSharkPlugin;
import com.loanshark.model.LoanData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LoanCommand implements CommandExecutor, TabCompleter {

    private final LoanSharkPlugin plugin;

    public LoanCommand(LoanSharkPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("dayun")) {
            return handleDayun(sender, args);
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            return handleAdmin(sender, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以使用此命令。");
            return true;
        }

        if (!player.hasPermission("loanshark.use")) {
            player.sendMessage(ChatColor.RED + "你没有权限使用高利贷服务。");
            return true;
        }

        if (args.length == 0) {
            plugin.getLoanGUI().open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info":
                showLoanInfo(player);
                break;
            case "help":
                showHelp(player);
                break;
            default:
                plugin.getLoanGUI().open(player);
                break;
        }
        return true;
    }

    private void showLoanInfo(Player player) {
        LoanData loanData = plugin.getLoanManager().getLoanData(player);
        long interestIntervalMs = plugin.getLoanManager().getInterestIntervalMs();
        int overdueDays = loanData.getOverdueDays(interestIntervalMs);
        long nextInterest = loanData.getNextInterestTime(interestIntervalMs);
        long untilNext = Math.max(0, (nextInterest - System.currentTimeMillis()) / 1000);

        String infoMsg = plugin.getConfig().getString("messages.loan_info",
                "&e===== &c高利贷信息 &e=====\n" +
                        "&7主动贷款: &c${active}\n" +
                        "&7被动贷款: &c${passive}\n" +
                        "&7利率: &c{rate}%&7/天\n" +
                        "&7逾期天数: &c{overdue}\n" +
                        "&7下次利息计算: &c{next_interest}");

        String nextInterestStr;
        if (loanData.hasAnyLoan() && untilNext > 0) {
            nextInterestStr = untilNext / 60 + "分" + untilNext % 60 + "秒";
        } else {
            nextInterestStr = "无";
        }

        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                infoMsg.replace("{active}", String.format("%.0f", loanData.getActiveLoanAmount()))
                        .replace("{passive}", String.format("%.0f", loanData.getPassiveLoanAmount()))
                        .replace("{rate}", String.format("%.0f", plugin.getLoanManager().getInterestRate() * 100))
                        .replace("{overdue}", String.valueOf(overdueDays))
                        .replace("{next_interest}", nextInterestStr)));
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "===== 高利贷帮助 =====");
        player.sendMessage(ChatColor.YELLOW + "/gaolidai " + ChatColor.GRAY + "- 打开高利贷GUI");
        player.sendMessage(ChatColor.YELLOW + "/gaolidai info " + ChatColor.GRAY + "- 查看贷款状态");
        if (player.hasPermission("loanshark.admin")) {
            player.sendMessage(ChatColor.YELLOW + "/gaolidai dayun <玩家> " + ChatColor.GRAY + "- 释放大运");
            player.sendMessage(ChatColor.YELLOW + "/gaolidai admin add <玩家> <金额> " + ChatColor.GRAY + "- 添加主动贷款");
            player.sendMessage(ChatColor.YELLOW + "/gaolidai admin remove <玩家> <金额> " + ChatColor.GRAY + "- 减少贷款");
            player.sendMessage(ChatColor.YELLOW + "/gaolidai admin info <玩家> " + ChatColor.GRAY + "- 查看他人贷款");
            player.sendMessage(ChatColor.YELLOW + "/gaolidai admin reset <玩家> " + ChatColor.GRAY + "- 清除所有贷款");
        }
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("loanshark.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有管理员权限。");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "/gaolidai admin add <玩家> <金额> [active|passive]");
            sender.sendMessage(ChatColor.YELLOW + "/gaolidai admin remove <玩家> <金额> [active|passive]");
            sender.sendMessage(ChatColor.YELLOW + "/gaolidai admin info <玩家>");
            sender.sendMessage(ChatColor.YELLOW + "/gaolidai admin reset <玩家>");
            return true;
        }
        String sub = args[1].toLowerCase();
        if (sub.equals("info")) {
            if (args.length < 3) { sender.sendMessage(ChatColor.RED + "用法: /gaolidai admin info <玩家>"); return true; }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) { sender.sendMessage(ChatColor.RED + "玩家不在线。"); return true; }
            showLoanInfo(sender, target);
            return true;
        }
        if (sub.equals("reset")) {
            if (args.length < 3) { sender.sendMessage(ChatColor.RED + "用法: /gaolidai admin reset <玩家>"); return true; }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) { sender.sendMessage(ChatColor.RED + "玩家不在线。"); return true; }
            LoanData data = plugin.getLoanManager().getLoanData(target);
            data.setActiveLoanAmount(0);
            data.setPassiveLoanAmount(0);
            data.setActiveLoanTimestamp(0);
            data.setPassiveLoanTimestamp(0);
            data.setLastInterestCalc(0);
            data.setLastPassiveInterestCalc(0);
            plugin.getLoanManager().save(data);
            sender.sendMessage(ChatColor.GREEN + "已清除 " + target.getName() + " 的所有贷款。");
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "用法: /gaolidai admin " + sub + " <玩家> <金额> [active|passive]");
            return true;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) { sender.sendMessage(ChatColor.RED + "玩家不在线。"); return true; }
        double amount;
        try { amount = Double.parseDouble(args[3]); } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "金额必须是数字。"); return true;
        }
        boolean isPassive = args.length >= 5 && args[4].equalsIgnoreCase("passive");
        LoanData data = plugin.getLoanManager().getLoanData(target);

        if (sub.equals("add")) {
            if (isPassive) {
                data.setPassiveLoanAmount(data.getPassiveLoanAmount() + amount);
                if (data.getPassiveLoanTimestamp() == 0) data.setPassiveLoanTimestamp(System.currentTimeMillis());
            } else {
                data.setActiveLoanAmount(data.getActiveLoanAmount() + amount);
                if (data.getActiveLoanTimestamp() == 0) data.setActiveLoanTimestamp(System.currentTimeMillis());
                data.setLastInterestCalc(System.currentTimeMillis());
            }
            plugin.getLoanManager().save(data);
            sender.sendMessage(ChatColor.GREEN + "已为 " + target.getName() + " 添加 "
                    + String.format("%.0f", amount) + " 的" + (isPassive ? "被动" : "主动") + "贷款。");
        } else if (sub.equals("remove")) {
            if (isPassive) {
                data.setPassiveLoanAmount(Math.max(0, data.getPassiveLoanAmount() - amount));
                if (data.getPassiveLoanAmount() <= 0) {
                    data.setPassiveLoanAmount(0);
                    data.setPassiveLoanTimestamp(0);
                    data.setLastPassiveInterestCalc(0);
                }
            } else {
                data.setActiveLoanAmount(Math.max(0, data.getActiveLoanAmount() - amount));
                if (data.getActiveLoanAmount() <= 0) {
                    data.setActiveLoanAmount(0);
                    data.setActiveLoanTimestamp(0);
                    data.setLastInterestCalc(0);
                }
            }
            plugin.getLoanManager().save(data);
            sender.sendMessage(ChatColor.GREEN + "已从 " + target.getName() + " 移除 "
                    + String.format("%.0f", amount) + " 的" + (isPassive ? "被动" : "主动") + "贷款。");
        } else {
            sender.sendMessage(ChatColor.RED + "未知操作: " + sub + "，可用: add/remove/info/reset");
        }
        return true;
    }

    private void showLoanInfo(CommandSender sender, Player target) {
        LoanData loanData = plugin.getLoanManager().getLoanData(target);
        long intervalMs = plugin.getLoanManager().getInterestIntervalMs();
        int overdue = loanData.getOverdueDays(intervalMs);

        sender.sendMessage(ChatColor.GOLD + "===== " + target.getName() + " 的高利贷信息 =====");
        sender.sendMessage(ChatColor.YELLOW + "主动贷款: " + ChatColor.RED + String.format("%.0f", loanData.getActiveLoanAmount()));
        sender.sendMessage(ChatColor.YELLOW + "被动贷款: " + ChatColor.RED + String.format("%.0f", loanData.getPassiveLoanAmount()));
        sender.sendMessage(ChatColor.YELLOW + "利率: " + ChatColor.RED + String.format("%.0f", plugin.getLoanManager().getInterestRate() * 100) + "%/天");
        sender.sendMessage(ChatColor.YELLOW + "逾期天数: " + ChatColor.RED + overdue);
        sender.sendMessage(ChatColor.YELLOW + "余额: " + ChatColor.GREEN + String.format("%.0f", plugin.getLoanManager().getBalance(target)));
    }

    private boolean handleDayun(CommandSender sender, String[] args) {
        if (!sender.hasPermission("loanshark.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令。");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /gaolidai dayun <玩家名>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "玩家 " + args[1] + " 不在线。");
            return true;
        }
        plugin.getPunishmentManager().triggerDayun(target);
        sender.sendMessage(ChatColor.GREEN + "已对 " + target.getName() + " 释放大运！");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            if ("info".startsWith(prefix)) completions.add("info");
            if ("help".startsWith(prefix)) completions.add("help");
            if (sender.hasPermission("loanshark.admin")) {
                if ("dayun".startsWith(prefix)) completions.add("dayun");
                if ("admin".startsWith(prefix)) completions.add("admin");
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("dayun") && sender.hasPermission("loanshark.admin")) {
            String prefix = args[1].toLowerCase();
            completions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix)).collect(Collectors.toList()));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("loanshark.admin")) {
            String prefix = args[1].toLowerCase();
            for (String s : new String[]{"add", "remove", "info", "reset"}) {
                if (s.startsWith(prefix)) completions.add(s);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("loanshark.admin")) {
            String prefix = args[2].toLowerCase();
            completions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix)).collect(Collectors.toList()));
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("admin")
                && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))
                && sender.hasPermission("loanshark.admin")) {
            String prefix = args[4].toLowerCase();
            if ("active".startsWith(prefix)) completions.add("active");
            if ("passive".startsWith(prefix)) completions.add("passive");
        }
        return completions;
    }
}
