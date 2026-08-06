package com.ringwatch.fraudring.service;

import java.util.Set;

public record RingContext(Set<String> memberAccountIds, String triggerDescription) {
}
