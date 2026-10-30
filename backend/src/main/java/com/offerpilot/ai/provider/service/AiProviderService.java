package com.offerpilot.ai.provider.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.offerpilot.ai.provider.domain.AiProvider;
import com.offerpilot.ai.provider.mapper.AiProviderMapper;
import com.offerpilot.ai.provider.web.dto.AiProviderPreset;
import com.offerpilot.ai.provider.web.dto.AiProviderRequest;
import com.offerpilot.ai.provider.web.dto.AiProviderStatus;
import com.offerpilot.ai.provider.web.dto.AiProviderTestResult;
import com.offerpilot.ai.provider.web.dto.AiProviderView;
import com.offerpilot.ai.service.AiChatClientFactory;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
public class AiProviderService {

    private final AiProviderMapper providerMapper;
    private final ApiKeyCipher apiKeyCipher;
    private final AiChatClientFactory chatClientFactory;
    private final boolean aiEnabled;
    private final String defaultModel;
    private final boolean allowPrivateBaseUrl;

    public AiProviderService(AiProviderMapper providerMapper,
                             ApiKeyCipher apiKeyCipher,
                             AiChatClientFactory chatClientFactory,
                             @Value("${app.ai.enabled:false}") boolean aiEnabled,
                             @Value("${spring.ai.openai.chat.options.model:deepseek-chat}") String defaultModel,
                             @Value("${app.ai.allow-private-base-url:false}") boolean allowPrivateBaseUrl) {
        this.providerMapper = providerMapper;
        this.apiKeyCipher = apiKeyCipher;
        this.chatClientFactory = chatClientFactory;
        this.aiEnabled = aiEnabled;
        this.defaultModel = defaultModel;
        this.allowPrivateBaseUrl = allowPrivateBaseUrl;
    }

    public List<AiProviderView> list(Long userId) {
        return providerMapper.selectList(new LambdaQueryWrapper<AiProvider>()
                        .eq(AiProvider::getUserId, userId)
                        .orderByDesc(AiProvider::getActive)
                        .orderByDesc(AiProvider::getUpdatedAt))
                .stream()
                .map(AiProviderView::from)
                .toList();
    }

    public List<AiProviderPreset> presets() {
        return Arrays.stream(AiProviderType.values())
                .map(AiProviderPreset::from)
                .toList();
    }

    public AiProviderStatus status() {
        return new AiProviderStatus(aiEnabled, defaultModel);
    }

    @Transactional
    public AiProviderView create(Long userId, AiProviderRequest request) {
        String name = request.name().trim();
        validateName(userId, name, null);

        AiProviderType type = AiProviderType.from(request.providerType());
        String baseUrl = resolveBaseUrl(type, request.baseUrl());
        String model = resolveModel(type, request.model());
        String apiKey = request.apiKey() == null ? "" : request.apiKey().trim();
        if (apiKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "API Key 不能为空");
        }

        AiProvider provider = new AiProvider();
        provider.setUserId(userId);
        provider.setName(name);
        provider.setProviderType(type.name());
        provider.setBaseUrl(baseUrl);
        provider.setModel(model);
        provider.setApiKeyCipher(apiKeyCipher.encrypt(apiKey));
        provider.setApiKeyHint(apiKeyCipher.mask(apiKey));
        provider.setTemperature(resolveTemperature(request.temperature()));
        provider.setEnabled(request.enabled() == null || request.enabled() ? 1 : 0);
        provider.setActive(providerMapper.selectCount(new LambdaQueryWrapper<AiProvider>()
                .eq(AiProvider::getUserId, userId)) == 0 ? 1 : 0);
        providerMapper.insert(provider);
        return AiProviderView.from(provider);
    }

    @Transactional
    public AiProviderView update(Long userId, Long id, AiProviderRequest request) {
        AiProvider provider = getOwned(userId, id);
        String name = request.name().trim();
        validateName(userId, name, id);

        AiProviderType type = AiProviderType.from(request.providerType());
        String baseUrl = resolveBaseUrl(type, request.baseUrl());
        String apiKey = request.apiKey() == null ? "" : request.apiKey().trim();
        boolean endpointChanged = !type.name().equals(provider.getProviderType())
                || !baseUrl.equals(provider.getBaseUrl());
        if (endpointChanged && apiKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "修改服务商或 Base URL 需要重新输入 API Key");
        }
        provider.setName(name);
        provider.setProviderType(type.name());
        provider.setBaseUrl(baseUrl);
        provider.setModel(resolveModel(type, request.model()));
        if (!apiKey.isBlank()) {
            provider.setApiKeyCipher(apiKeyCipher.encrypt(apiKey));
            provider.setApiKeyHint(apiKeyCipher.mask(apiKey));
        }
        provider.setTemperature(resolveTemperature(request.temperature()));
        if (request.enabled() != null) {
            provider.setEnabled(request.enabled() ? 1 : 0);
        }
        providerMapper.updateById(provider);

        if (provider.getActive() == null || provider.getActive() == 0) {
            ensureActive(userId);
        }
        return AiProviderView.from(providerMapper.selectById(id));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        AiProvider provider = getOwned(userId, id);
        boolean wasActive = provider.getActive() != null && provider.getActive() == 1;
        providerMapper.deleteById(id);
        if (wasActive) {
            AiProvider next = providerMapper.selectOne(new LambdaQueryWrapper<AiProvider>()
                    .eq(AiProvider::getUserId, userId)
                    .orderByDesc(AiProvider::getUpdatedAt)
                    .last("LIMIT 1"));
            if (next != null) {
                next.setActive(1);
                next.setEnabled(1);
                providerMapper.updateById(next);
            }
        }
    }

    @Transactional
    public AiProviderView activate(Long userId, Long id) {
        AiProvider provider = getOwned(userId, id);
        providerMapper.update(null, new LambdaUpdateWrapper<AiProvider>()
                .eq(AiProvider::getUserId, userId)
                .set(AiProvider::getActive, 0));
        provider.setActive(1);
        provider.setEnabled(1);
        providerMapper.updateById(provider);
        return AiProviderView.from(provider);
    }

    public AiProvider activeForUser(Long userId) {
        if (userId == null) {
            return null;
        }
        return providerMapper.selectOne(new LambdaQueryWrapper<AiProvider>()
                .eq(AiProvider::getUserId, userId)
                .eq(AiProvider::getActive, 1)
                .eq(AiProvider::getEnabled, 1)
                .last("LIMIT 1"));
    }

    public AiProviderTestResult test(Long userId, Long id) {
        AiProvider provider = getOwned(userId, id);
        long start = System.currentTimeMillis();
        try {
            ChatClient chatClient = chatClientFactory.build(provider);
            String content = chatClient.prompt()
                    .user("请只回复 OK")
                    .call()
                    .content();
            long latencyMs = System.currentTimeMillis() - start;
            String message = content == null || content.isBlank() ? "连接成功" : content.trim();
            return new AiProviderTestResult(true, latencyMs, provider.getModel(), truncate(message));
        } catch (Exception ex) {
            long latencyMs = System.currentTimeMillis() - start;
            return new AiProviderTestResult(false, latencyMs, provider.getModel(),
                    truncate(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        }
    }

    public AiProvider getOwned(Long userId, Long id) {
        AiProvider provider = providerMapper.selectById(id);
        if (provider == null || !Objects.equals(provider.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API 配置不存在");
        }
        return provider;
    }

    private void ensureActive(Long userId) {
        AiProvider active = activeForUser(userId);
        if (active != null) {
            return;
        }
        AiProvider first = providerMapper.selectOne(new LambdaQueryWrapper<AiProvider>()
                .eq(AiProvider::getUserId, userId)
                .orderByDesc(AiProvider::getUpdatedAt)
                .last("LIMIT 1"));
        if (first != null) {
            first.setActive(1);
            first.setEnabled(1);
            providerMapper.updateById(first);
        }
    }

    private void validateName(Long userId, String name, Long excludeId) {
        LambdaQueryWrapper<AiProvider> wrapper = new LambdaQueryWrapper<AiProvider>()
                .eq(AiProvider::getUserId, userId)
                .eq(AiProvider::getName, name);
        if (excludeId != null) {
            wrapper.ne(AiProvider::getId, excludeId);
        }
        if (providerMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "配置名称已存在");
        }
    }

    private String resolveBaseUrl(AiProviderType type, String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.trim();
        if (value.isBlank()) {
            value = type.defaultBaseUrl();
        }
        if (value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Base URL 不能为空");
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        validateBaseUrl(value);
        return value;
    }

    private void validateBaseUrl(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Base URL 格式不正确");
        }
        String scheme = uri.getScheme();
        boolean https = scheme != null && scheme.equalsIgnoreCase("https");
        boolean localHttp = allowPrivateBaseUrl && scheme != null && scheme.equalsIgnoreCase("http");
        if (!https && !localHttp) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Base URL 必须使用 https");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Base URL 缺少主机名");
        }
        if (allowPrivateBaseUrl) {
            return;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Base URL 不能指向内网地址");
                }
            }
        } catch (UnknownHostException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Base URL 域名无法解析");
        }
    }

    private String resolveModel(AiProviderType type, String model) {
        String value = model == null ? "" : model.trim();
        if (value.isBlank()) {
            value = type.defaultModel();
        }
        if (value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型名称不能为空");
        }
        return value;
    }

    private double resolveTemperature(Double temperature) {
        if (temperature == null) {
            return 0.4;
        }
        return Math.max(0.0, Math.min(2.0, temperature));
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 200 ? value : value.substring(0, 200);
    }
}
