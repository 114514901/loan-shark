package com.loanshark;

import com.loanshark.command.LoanCommand;
import com.loanshark.gui.LoanGUI;
import com.loanshark.listener.LoanListener;
import com.loanshark.manager.LoanManager;
import com.loanshark.manager.PunishmentManager;
import com.loanshark.storage.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.Objects;

public class LoanSharkPlugin extends JavaPlugin {

    private Economy economy;
    private DatabaseManager dbManager;
    private LoanManager loanManager;
    private PunishmentManager punishmentManager;
    private LoanGUI loanGUI;

    private BukkitTask interestTask;
    private BukkitTask overdueCheckTask;
    private BukkitTask passiveLoanCheckTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vault经济系统未找到！插件已禁用。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        dbManager = new DatabaseManager(getDataFolder());
        try {
            dbManager.connect();
        } catch (SQLException e) {
            getLogger().severe("数据库连接失败！插件已禁用。");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        loanManager = new LoanManager(this, economy, dbManager);
        loanManager.loadLoans();

        extractDatapack();

        punishmentManager = new PunishmentManager(this);

        loanGUI = new LoanGUI(this);

        LoanCommand loanCommand = new LoanCommand(this);
        LoanListener loanListener = new LoanListener(this);
        Objects.requireNonNull(getCommand("gaolidai")).setExecutor(loanCommand);
        Objects.requireNonNull(getCommand("gaolidai")).setTabCompleter(loanCommand);
        getServer().getPluginManager().registerEvents(loanListener, this);

        startScheduledTasks();

        getLogger().info("LoanShark v" + getDescription().getVersion() + " 已启用！");
    }

    @Override
    public void onDisable() {
        stopScheduledTasks();
        if (loanManager != null) {
            loanManager.saveAll();
        }
        if (dbManager != null) {
            dbManager.close();
        }
        getLogger().info("LoanShark 已禁用！");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    private void startScheduledTasks() {
        long interestInterval = getConfig().getLong("settings.interest_interval_seconds", 1200) * 20L;

        interestTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            loanManager.calculateInterest();
        }, interestInterval, interestInterval);

        overdueCheckTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            loanManager.checkOverdueLoans();
        }, 600L, 600L);

        passiveLoanCheckTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                double balance = loanManager.getBalance(player);
                if (balance < 0) {
                    loanManager.handlePassiveLoan(player);
                }
                loanManager.checkAndUpdatePassiveLoanRepayment(player);
            }
        }, 100L, 100L);
    }

    private void stopScheduledTasks() {
        if (interestTask != null) interestTask.cancel();
        if (overdueCheckTask != null) overdueCheckTask.cancel();
        if (passiveLoanCheckTask != null) passiveLoanCheckTask.cancel();
    }

    private void extractDatapack() {
        File worldDir = getServer().getWorlds().get(0).getWorldFolder();
        File datapacksDir = new File(worldDir, "datapacks/loan-shark");
        if (datapacksDir.exists()) return;

        datapacksDir.mkdirs();
        String[] files = {"pack.mcmeta", "data/loanshark/jukebox_song/dayun.json"};
        for (String file : files) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("datapack/" + file)) {
                if (in == null) {
                    getLogger().warning("datapack资源未找到: " + file);
                    continue;
                }
                File outFile = new File(datapacksDir, file);
                outFile.getParentFile().mkdirs();
                Files.copy(in, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                getLogger().severe("提取datapack失败: " + file);
                e.printStackTrace();
            }
        }
        getLogger().info("大运唱片数据包已安装到: " + datapacksDir.getPath());
        getLogger().info("请手动执行 /minecraft:reload 以加载数据包");
    }

    public LoanManager getLoanManager() { return loanManager; }
    public PunishmentManager getPunishmentManager() { return punishmentManager; }
    public LoanGUI getLoanGUI() { return loanGUI; }
}
