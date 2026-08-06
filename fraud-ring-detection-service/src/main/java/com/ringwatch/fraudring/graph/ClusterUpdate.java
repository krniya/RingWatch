package com.ringwatch.fraudring.graph;

import java.util.Set;

public record ClusterUpdate(Set<String> memberAccountIds, String triggeringDeviceId, String triggeringIpAddress) {
}
