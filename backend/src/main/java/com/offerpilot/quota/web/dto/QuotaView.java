package com.offerpilot.quota.web.dto;

import java.time.LocalDateTime;

public record QuotaView(
        String plan,
        Integer sessionLimit,
        Integer sessionUsed,
        int remainingSessions,
        Integer tokenLimit,
        Integer tokenUsed,
        LocalDateTime resetAt
) {
}
