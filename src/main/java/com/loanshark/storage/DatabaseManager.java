package com.loanshark.storage;

import com.loanshark.model.LoanData;

import java.io.File;
import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseManager {

    private final File pluginDir;
    private Connection connection;

    public DatabaseManager(File pluginDir) {
        this.pluginDir = pluginDir;
    }

    public void connect() throws SQLException {
        File dbFile = new File(pluginDir, "loans.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        createTable();
    }

    private void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS loans (" +
                "uuid TEXT PRIMARY KEY," +
                "active_loan_amount REAL DEFAULT 0," +
                "passive_loan_amount REAL DEFAULT 0," +
                "active_loan_timestamp INTEGER DEFAULT 0," +
                "passive_loan_timestamp INTEGER DEFAULT 0," +
                "last_interest_calc INTEGER DEFAULT 0," +
                "last_passive_interest_calc INTEGER DEFAULT 0," +
                "last_punishment_time INTEGER DEFAULT 0" +
                ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
        }
    }

    public Map<UUID, LoanData> loadAll() {
        Map<UUID, LoanData> loans = new ConcurrentHashMap<>();
        String sql = "SELECT * FROM loans";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                LoanData data = new LoanData(uuid);
                data.setActiveLoanAmount(rs.getDouble("active_loan_amount"));
                data.setPassiveLoanAmount(rs.getDouble("passive_loan_amount"));
                data.setActiveLoanTimestamp(rs.getLong("active_loan_timestamp"));
                data.setPassiveLoanTimestamp(rs.getLong("passive_loan_timestamp"));
                data.setLastInterestCalc(rs.getLong("last_interest_calc"));
                data.setLastPassiveInterestCalc(rs.getLong("last_passive_interest_calc"));
                data.setLastPunishmentTime(rs.getLong("last_punishment_time"));
                loans.put(uuid, data);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return loans;
    }

    public void save(LoanData data) {
        String sql = "INSERT OR REPLACE INTO loans (uuid, active_loan_amount, passive_loan_amount, " +
                "active_loan_timestamp, passive_loan_timestamp, last_interest_calc, last_passive_interest_calc, last_punishment_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, data.getPlayerUuid().toString());
            ps.setDouble(2, data.getActiveLoanAmount());
            ps.setDouble(3, data.getPassiveLoanAmount());
            ps.setLong(4, data.getActiveLoanTimestamp());
            ps.setLong(5, data.getPassiveLoanTimestamp());
            ps.setLong(6, data.getLastInterestCalc());
            ps.setLong(7, data.getLastPassiveInterestCalc());
            ps.setLong(8, data.getLastPunishmentTime());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveAll(Map<UUID, LoanData> loans) {
        for (LoanData data : loans.values()) {
            save(data);
        }
    }
}
