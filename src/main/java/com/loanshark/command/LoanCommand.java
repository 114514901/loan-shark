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
            case "admin":
                handleAdmin(sender, args);
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
            player.sendMessage(ChatColor.YELLOW + "/gaolidai dayun <玩家> " + ChatColor.GRAY + "- 对玩家释放大运");
        }
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("loanshark.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有管理员权限。");
            return;
        }
        sender.sendMessage(ChatColor.GRAY + "管理员功能待实现。");
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
            if (sender.hasPermission("loanshark.admin") && "dayun".startsWith(prefix)) completions.add("dayun");
            if (sender.hasPermission("loanshark.admin") && "admin".startsWith(prefix)) completions.add("admin");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("dayun") && sender.hasPermission("loanshark.admin")) {
            String prefix = args[1].toLowerCase();
            completions.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList()));
        }
        return completions;
    }
}
