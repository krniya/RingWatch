package com.ringwatch.enrichment.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;

public record AccountHistory(
        int recentTxnCount,
        BigDecimal totalAmount,
        Set<String> knownDevices,
        Set<String> knownIps
) {

    public static AccountHistory empty() {
        return new AccountHistory(0, BigDecimal.ZERO, Set.of(), Set.of());
    }

    public BigDecimal averageAmount() {
        if (recentTxnCount == 0) {
            return BigDecimal.ZERO;
        }
        return totalAmount.divide(BigDecimal.valueOf(recentTxnCount), 2, RoundingMode.HALF_UP);
    }

    public AccountHistory withTransaction(String deviceId, String ipAddress, BigDecimal amount) {
        Set<String> devices = new HashSet<>(knownDevices);
        devices.add(deviceId);
        Set<String> ips = new HashSet<>(knownIps);
        ips.add(ipAddress);
        return new AccountHistory(recentTxnCount + 1, totalAmount.add(amount), Set.copyOf(devices), Set.copyOf(ips));
    }
}
