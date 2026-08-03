package com.loanshark.manager;

import com.loanshark.LoanSharkPlugin;
import com.loanshark.model.LoanData;
import com.loanshark.storage.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LoanManager {

    private final LoanSharkPlugin plugin;
    private final Economy economy;
    private final DatabaseManager dbManager;
    private final Map<UUID, LoanData> loans;

    private double interestRate;
    private long interestIntervalMs;
    private int overdueDays;
    private double passiveLoanMax;

    public LoanManager(LoanSharkPlugin plugin, Economy economy, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.economy = economy;
        this.dbManager = dbManager;
        this.loans = new ConcurrentHashMap<>();
        loadConfig();
    }

    public void loadConfig() {
        this.interestRate = plugin.getConfig().getDouble("settings.interest_rate", 0.20);
        this.interestIntervalMs = plugin.getConfig().getLong("settings.interest_interval_seconds", 1200) * 1000L;
        this.overdueDays = plugin.getConfig().getInt("settings.overdue_days", 3);
        this.passiveLoanMax = plugin.getConfig().getDouble("settings.passive_loan_max", 10000.0);
    }

    public void loadLoans() {
        loans.clear();
        loans.putAll(dbManager.loadAll());
    }

    public void saveAll() {
        dbManager.saveAll(loans);
    }

    public LoanData getLoanData(Player player) {
        return loans.computeIfAbsent(player.getUniqueId(), LoanData::new);
    }

    public LoanData getLoanData(UUID uuid) {
        return loans.get(uuid);
    }

    public double getBalance(Player player) {
        return economy.getBalance(player);
    }

    public void takeActiveLoan(Player player, double amount) {
        LoanData data = getLoanData(player);
        if (data.hasPassiveLoan()) {
            double cur = data.getPassiveLoanAmount();
            data.setActiveLoanAmount(cur + amount);
            data.setPassiveLoanAmount(0);
            data.setActiveLoanTimestamp(System.currentTimeMillis());
            data.setLastInterestCalc(System.currentTimeMillis());
            data.setPassiveLoanTimestamp(0);
            data.setLastPassiveInterestCalc(0);
            if (cur > 0) {
                plugin.getLogger().info(player.getName() + " passive loan converted to active: " + cur);
            }
        } else {
            data.setActiveLoanAmount(data.getActiveLoanAmount() + amount);
            data.setActiveLoanTimestamp(System.currentTimeMillis());
            data.setLastInterestCalc(System.currentTimeMillis());
        }
        economy.depositPlayer(player, amount);
        save(data);
        grantAdvancement(player, "first_loan");

        String msg = plugin.getConfig().getString("messages.active_loan_taken", "&e你借了 &a${amount}&e！利率: &c{rate}%&e/天，利滚利，&c{days}&e天后触发惩罚！");
        sendMessage(player, msg
                .replace("{amount}", String.format("%.0f", amount))
                .replace("{rate}", String.format("%.0f", interestRate * 100))
                .replace("{days}", String.valueOf(overdueDays)));
    }

    public void repayActiveLoan(Player player, double amount) {
        LoanData data = getLoanData(player);
        if (!data.hasActiveLoan()) {
            sendMessage(player, plugin.getConfig().getString("messages.no_loan", "&7你当前没有贷款。"));
            return;
        }
        double balance = getBalance(player);
        double actualRepay = Math.min(amount, Math.min(data.getActiveLoanAmount(), balance));
        if (actualRepay <= 0) {
            sendMessage(player, plugin.getConfig().getString("messages.insufficient_balance_repay", "&c你没有足够的钱来还款！"));
            return;
        }
        economy.withdrawPlayer(player, actualRepay);
        data.setActiveLoanAmount(data.getActiveLoanAmount() - actualRepay);
        if (data.getActiveLoanAmount() <= 0) {
            data.setActiveLoanAmount(0);
            data.setActiveLoanTimestamp(0);
            data.setLastInterestCalc(0);
            sendMessage(player, plugin.getConfig().getString("messages.active_loan_repaid", "&a你已还清全部贷款！"));
            grantAdvancement(player, "repay");
        } else {
            data.setLastInterestCalc(System.currentTimeMillis());
            sendMessage(player, plugin.getConfig().getString("messages.active_loan_partial", "&a已偿还 &a${amount}&e，剩余: &c${remaining}")
                    .replace("{amount}", String.format("%.0f", actualRepay))
                    .replace("{remaining}", String.format("%.0f", data.getActiveLoanAmount())));
        }
        save(data);
    }

    public void handlePassiveLoan(Player player) {
        double balance = getBalance(player);
        if (balance >= 0) return;

        LoanData data = getLoanData(player);
        double deficit = -balance;
        double maxLoan = Math.min(deficit, passiveLoanMax);

        if (data.hasActiveLoan()) {
            sendMessage(player, plugin.getConfig().getString("messages.negative_balance_warning",
                    "&c警告，你的余额已超支，我们将为您提供高利贷服务，请及时还款！"));
            return;
        }

        if (!data.hasPassiveLoan()) {
            data.setPassiveLoanAmount(maxLoan);
            data.setPassiveLoanTimestamp(System.currentTimeMillis());
            data.setLastInterestCalc(System.currentTimeMillis());
            data.setLastPassiveInterestCalc(System.currentTimeMillis());
            sendMessage(player, plugin.getConfig().getString("messages.negative_balance_warning",
                    "&c警告，你的余额已超支，我们将为您提供高利贷服务，请及时还款！"));
            sendMessage(player, plugin.getConfig().getString("messages.passive_loan_given", "&e已为你提供被动高利贷: &c{days}&e 个游戏日内未还清将触发惩罚！")
                    .replace("{days}", String.valueOf(overdueDays)));
            save(data);
        } else {
            double additional = Math.max(0, deficit - data.getPassiveLoanAmount());
            if (additional > 0) {
                double newLoan = Math.min(data.getPassiveLoanAmount() + additional, passiveLoanMax);
                economy.depositPlayer(player, newLoan - data.getPassiveLoanAmount());
                data.setPassiveLoanAmount(newLoan);
                data.setLastInterestCalc(System.currentTimeMillis());
                sendMessage(player, plugin.getConfig().getString("messages.negative_balance_warning",
                        "&c警告，你的余额已超支，我们将为您提供高利贷服务，请及时还款！"));
                save(data);
            }
        }
    }

    public void checkAndUpdatePassiveLoanRepayment(Player player) {
        LoanData data = getLoanData(player);
        if (!data.hasPassiveLoan()) return;

        long now = System.currentTimeMillis();
        if (data.getLastPassiveInterestCalc() == 0) {
            data.setLastPassiveInterestCalc(now);
        }
        long elapsed = now - data.getLastPassiveInterestCalc();
        int periods = (int) (elapsed / interestIntervalMs);
        if (periods > 0) {
            double balance = getBalance(player);
            for (int i = 0; i < periods; i++) {
                double interest = Math.round(data.getPassiveLoanAmount() * interestRate * 100.0) / 100.0;
                if (balance >= interest && balance > 0) {
                    economy.withdrawPlayer(player, interest);
                    balance -= interest;
                } else {
                    if (balance > 0) {
                        economy.withdrawPlayer(player, balance);
                        interest -= balance;
                        balance = 0;
                    }
                    data.setPassiveLoanAmount(data.getPassiveLoanAmount() + interest);
                }
            }
            data.setLastPassiveInterestCalc(data.getLastPassiveInterestCalc() + (long) periods * interestIntervalMs);
        }

        double balance = getBalance(player);
        if (balance > 0 && data.hasPassiveLoan()) {
            double payment = Math.min(balance, data.getPassiveLoanAmount());
            economy.withdrawPlayer(player, payment);
            data.setPassiveLoanAmount(data.getPassiveLoanAmount() - payment);
        }

        if (data.getPassiveLoanAmount() < 0.01) {
            data.setPassiveLoanAmount(0);
            data.setPassiveLoanTimestamp(0);
            data.setLastPassiveInterestCalc(0);
            sendMessage(player, plugin.getConfig().getString("messages.passive_loan_repaid", "&a你的被动高利贷已还清！"));
        }

        save(data);
    }

    public void calculateInterest() {
        long now = System.currentTimeMillis();
        for (LoanData data : loans.values()) {
            if (!data.hasActiveLoan()) continue;
            if (data.getLastInterestCalc() <= 0) {
                data.setLastInterestCalc(now);
                continue;
            }
            long elapsed = now - data.getLastInterestCalc();
            if (elapsed < interestIntervalMs) continue;

            int periods = (int) (elapsed / interestIntervalMs);
            if (periods <= 0) continue;

            if (data.hasActiveLoan()) {
                double rate = interestRate;
                double amount = data.getActiveLoanAmount();
                for (int i = 0; i < periods; i++) {
                    amount *= (1 + rate);
                }
                data.setActiveLoanAmount(Math.round(amount * 100.0) / 100.0);
            }
            data.setLastInterestCalc(data.getLastInterestCalc() + (long) periods * interestIntervalMs);
            save(data);
        }
    }

    public void checkOverdueLoans() {
        for (Map.Entry<UUID, LoanData> entry : loans.entrySet()) {
            LoanData data = entry.getValue();
            if (!data.hasAnyLoan()) continue;

            int overdueDays = data.getOverdueDays(interestIntervalMs);
            if (overdueDays >= this.overdueDays) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    plugin.getPunishmentManager().triggerPunishment(player, data);
                }
            }
        }
    }

    public void reduceLoanOnPunishment(LoanData data, Player player) {
        double reduction = plugin.getConfig().getDouble("settings.punishment_loan_reduction", 0.20);
        if (data.hasActiveLoan()) {
            double forgiven = data.getActiveLoanAmount() * reduction;
            data.setActiveLoanAmount(data.getActiveLoanAmount() - forgiven);
            economy.depositPlayer(player, forgiven);
            data.setActiveLoanTimestamp(System.currentTimeMillis());
            data.setLastInterestCalc(System.currentTimeMillis());
            if (data.getActiveLoanAmount() < 0.01) {
                data.setActiveLoanAmount(0);
                data.setActiveLoanTimestamp(0);
                data.setLastInterestCalc(0);
            }
        }
        if (data.hasPassiveLoan()) {
            double forgiven = data.getPassiveLoanAmount() * reduction;
            data.setPassiveLoanAmount(data.getPassiveLoanAmount() - forgiven);
            economy.depositPlayer(player, forgiven);
            data.setPassiveLoanTimestamp(System.currentTimeMillis());
            data.setLastPassiveInterestCalc(System.currentTimeMillis());
            if (data.getPassiveLoanAmount() < 0.01) {
                data.setPassiveLoanAmount(0);
                data.setPassiveLoanTimestamp(0);
                data.setLastInterestCalc(0);
                data.setLastPassiveInterestCalc(0);
            }
        }
        data.setLastPunishmentTime(System.currentTimeMillis());
        save(data);
    }

    public void autoDeduct(Player player, LoanData data) {
        if (!data.hasAnyLoan()) return;
        double balance = getBalance(player);
        if (balance <= 0) return;

        if (data.hasActiveLoan()) {
            double deduct = Math.min(balance, data.getActiveLoanAmount());
            economy.withdrawPlayer(player, deduct);
            data.setActiveLoanAmount(data.getActiveLoanAmount() - deduct);
            if (data.getActiveLoanAmount() < 0.01) {
                data.setActiveLoanAmount(0);
                data.setActiveLoanTimestamp(0);
                data.setLastInterestCalc(0);
            }
            sendMessage(player, plugin.getConfig().getString("messages.auto_deducted", "&e已自动扣除 &c${amount}")
                    .replace("{amount}", String.format("%.0f", deduct)));
            save(data);
            return;
        }

        if (data.hasPassiveLoan()) {
            double deduct = Math.min(balance, data.getPassiveLoanAmount());
            economy.withdrawPlayer(player, deduct);
            data.setPassiveLoanAmount(data.getPassiveLoanAmount() - deduct);
            if (data.getPassiveLoanAmount() < 0.01) {
                data.setPassiveLoanAmount(0);
                data.setPassiveLoanTimestamp(0);
                data.setLastInterestCalc(0);
            }
            sendMessage(player, plugin.getConfig().getString("messages.auto_deducted", "&e已自动扣除 &c${amount}")
                    .replace("{amount}", String.format("%.0f", deduct)));
            save(data);
        }
    }

    public double getInterestRate() { return interestRate; }
    public long getInterestIntervalMs() { return interestIntervalMs; }
    public int getOverdueDays() { return overdueDays; }

    public void save(LoanData data) {
        dbManager.save(data);
    }

    private void grantAdvancement(Player player, String name) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "advancement grant " + player.getName() + " only loanshark:" + name);
    }

    private void sendMessage(Player player, String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&c高利贷&8]&7 ");
        player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', prefix + message));
    }
}
