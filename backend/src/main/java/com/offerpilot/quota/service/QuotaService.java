package com.offerpilot.quota.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.quota.domain.UserQuota;
import com.offerpilot.quota.mapper.UserQuotaMapper;
import com.offerpilot.quota.web.dto.QuotaView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class QuotaService {

    private final UserQuotaMapper quotaMapper;
    private final int freeDailySessions;
    private final int freeDailyTokens;
    private final boolean enforce;

    public QuotaService(UserQuotaMapper quotaMapper,
                        @Value("${app.quota.free-daily-sessions:3}") int freeDailySessions,
                        @Value("${app.quota.free-daily-tokens:200000}") int freeDailyTokens,
                        @Value("${app.quota.enforce:false}") boolean enforce) {
        this.quotaMapper = quotaMapper;
        this.freeDailySessions = freeDailySessions;
        this.freeDailyTokens = freeDailyTokens;
        this.enforce = enforce;
    }

    @Transactional
    public void checkAvailable(Long userId) {
        if (!enforce || isAdmin()) {
            return;
        }
        requireAvailable(userId);
    }

    @Transactional
    public void checkAndConsumeSession(Long userId) {
        if (!enforce || isAdmin()) {
            return;
        }
        UserQuota quota = requireAvailable(userId);
        quota.setSessionUsed(quota.getSessionUsed() + 1);
        quotaMapper.updateById(quota);
    }

    private UserQuota requireAvailable(Long userId) {
        UserQuota quota = getOrCreate(userId);
        resetIfNeeded(quota);
        if (quota.getSessionUsed() >= quota.getSessionLimit()) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "今日面试额度已用完");
        }
        return quota;
    }

    public QuotaView me(Long userId) {
        if (!enforce) {
            return new QuotaView("UNLIMITED", -1, 0, -1, -1, 0, null);
        }
        if (isAdmin()) {
            return new QuotaView("ADMIN", -1, 0, -1, -1, 0, null);
        }
        UserQuota quota = getOrCreate(userId);
        resetIfNeeded(quota);
        int remaining = Math.max(0, quota.getSessionLimit() - quota.getSessionUsed());
        return new QuotaView(
                quota.getPlan(),
                quota.getSessionLimit(),
                quota.getSessionUsed(),
                remaining,
                quota.getTokenLimit(),
                quota.getTokenUsed(),
                quota.getPeriodEnd());
    }

    private UserQuota getOrCreate(Long userId) {
        UserQuota quota = quotaMapper.selectOne(
                new LambdaQueryWrapper<UserQuota>().eq(UserQuota::getUserId, userId));
        if (quota != null) {
            return quota;
        }
        LocalDateTime start = LocalDate.now().atStartOfDay();
        quota = new UserQuota();
        quota.setUserId(userId);
        quota.setPlan("FREE");
        quota.setPeriodStart(start);
        quota.setPeriodEnd(start.plusDays(1));
        quota.setSessionLimit(freeDailySessions);
        quota.setSessionUsed(0);
        quota.setTokenLimit(freeDailyTokens);
        quota.setTokenUsed(0);
        quotaMapper.insert(quota);
        return quota;
    }

    private void resetIfNeeded(UserQuota quota) {
        if (LocalDateTime.now().isAfter(quota.getPeriodEnd())) {
            LocalDateTime start = LocalDate.now().atStartOfDay();
            quota.setPeriodStart(start);
            quota.setPeriodEnd(start.plusDays(1));
            quota.setSessionUsed(0);
            quota.setTokenUsed(0);
            quotaMapper.updateById(quota);
        }
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
