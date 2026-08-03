package com.loanshark.model;

import java.util.UUID;

public class LoanData {
    private final UUID playerUuid;
    private double activeLoanAmount;
    private double passiveLoanAmount;
    private long activeLoanTimestamp;
    private long passiveLoanTimestamp;
    private long lastInterestCalc;
    private long lastPassiveInterestCalc;
    private long lastPunishmentTime;

    public LoanData(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.activeLoanAmount = 0;
        this.passiveLoanAmount = 0;
        this.activeLoanTimestamp = 0;
        this.passiveLoanTimestamp = 0;
        this.lastInterestCalc = 0;
        this.lastPassiveInterestCalc = 0;
        this.lastPunishmentTime = 0;
    }

    public UUID getPlayerUuid() { return playerUuid; }

    public double getActiveLoanAmount() { return activeLoanAmount; }
    public void setActiveLoanAmount(double activeLoanAmount) { this.activeLoanAmount = activeLoanAmount; }

    public double getPassiveLoanAmount() { return passiveLoanAmount; }
    public void setPassiveLoanAmount(double passiveLoanAmount) { this.passiveLoanAmount = passiveLoanAmount; }

    public long getActiveLoanTimestamp() { return activeLoanTimestamp; }
    public void setActiveLoanTimestamp(long activeLoanTimestamp) { this.activeLoanTimestamp = activeLoanTimestamp; }

    public long getPassiveLoanTimestamp() { return passiveLoanTimestamp; }
    public void setPassiveLoanTimestamp(long passiveLoanTimestamp) { this.passiveLoanTimestamp = passiveLoanTimestamp; }

    public long getLastInterestCalc() { return lastInterestCalc; }
    public void setLastInterestCalc(long lastInterestCalc) { this.lastInterestCalc = lastInterestCalc; }

    public long getLastPassiveInterestCalc() { return lastPassiveInterestCalc; }
    public void setLastPassiveInterestCalc(long lastPassiveInterestCalc) { this.lastPassiveInterestCalc = lastPassiveInterestCalc; }

    public long getLastPunishmentTime() { return lastPunishmentTime; }
    public void setLastPunishmentTime(long lastPunishmentTime) { this.lastPunishmentTime = lastPunishmentTime; }

    public double getTotalLoan() { return activeLoanAmount + passiveLoanAmount; }

    public boolean hasActiveLoan() { return activeLoanAmount > 0; }
    public boolean hasPassiveLoan() { return passiveLoanAmount > 0; }
    public boolean hasAnyLoan() { return hasActiveLoan() || hasPassiveLoan(); }

    public int getOverdueDays(long interestIntervalMs) {
        int maxDays = 0;
        long now = System.currentTimeMillis();
        if (activeLoanAmount > 0 && activeLoanTimestamp > 0) {
            int days = (int) ((now - activeLoanTimestamp) / interestIntervalMs);
            maxDays = Math.max(maxDays, days);
        }
        if (passiveLoanAmount > 0 && passiveLoanTimestamp > 0) {
            int days = (int) ((now - passiveLoanTimestamp) / interestIntervalMs);
            maxDays = Math.max(maxDays, days);
        }
        return maxDays;
    }

    public long getNextInterestTime(long interestIntervalMs) {
        if (lastInterestCalc == 0) return System.currentTimeMillis() + interestIntervalMs;
        return lastInterestCalc + interestIntervalMs;
    }
}
