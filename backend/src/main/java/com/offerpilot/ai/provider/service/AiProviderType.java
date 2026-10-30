package com.offerpilot.ai.provider.service;

import java.util.List;
import java.util.Locale;

public enum AiProviderType {

    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-chat", "国内官方",
            List.of("deepseek-chat", "deepseek-reasoner", "deepseek-flash", "deepseek-v4-flash")),
    MIMO("小米 MiMo", "https://api.xiaomimimo.com/v1", "mimo-v2.5", "国内官方",
            List.of("mimo-v2.5", "mimo-v2.6-pro", "mimo-v2.6-flash", "mimo-v2.6-pro-ultraspeed")),
    MIMO_TOKEN_PLAN("小米 MiMo Token Plan", "https://token-plan-cn.xiaomimimo.com/v1", "mimo-v2.5", "国内官方",
            List.of("mimo-v2.5", "mimo-v2.6-pro", "mimo-v2.6-flash")),
    KIMI("Kimi（月之暗面）", "https://api.moonshot.cn/v1", "kimi-k3", "国内官方",
            List.of("kimi-k3", "kimi-k2.7-code", "kimi-k2.6")),
    ZHIPU("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-5.3", "国内官方",
            List.of("glm-5.3", "glm-5.3-flash", "glm-5-turbo")),
    QWEN("通义千问（阿里云百炼）", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen3.8-max", "国内官方",
            List.of("qwen3.8-max", "qwen3.8-2.4t-a95b", "qwen3.8-27b", "qwen3.7-plus")),
    MINIMAX("MiniMax", "https://api.minimax.cn/v1", "MiniMax-M3.1-Flash", "国内官方",
            List.of("MiniMax-M3.1-Flash", "MiniMax-M3")),
    STEPFUN("阶跃星辰 StepFun", "https://api.stepfun.com/v1", "step-3.7-flash", "国内官方",
            List.of("step-3.7-flash", "step-3.5-flash-2603", "step-3.5-flash")),
    DOUBAO("火山方舟（豆包）", "https://ark.cn-beijing.volces.com/api/v3", "doubao-seed-2-1-pro-260915", "国内官方",
            List.of("doubao-seed-2-1-pro-260915", "doubao-seed-1-6-250615", "doubao-seed-1-6-flash-250615")),
    QIANFAN("百度千帆", "https://qianfan.baidubce.com/v2", "deepseek-v4-flash", "国内官方",
            List.of("deepseek-v4-flash", "qianfan-code-latest", "ernie-4.5-turbo-128k")),
    HUNYUAN("腾讯混元", "https://tokenhub.tencentmaas.com/v1", "hy3", "国内官方",
            List.of("hy3", "hy4-preview", "hy3-preview")),
    TENCENT_TOKEN_PLAN("腾讯 Token Plan", "https://api.lkeap.cloud.tencent.com/plan/v3", "tc-code-latest", "国内官方",
            List.of("tc-code-latest", "deepseek-v4-flash-202605", "minimax-m2.7",
                    "glm-5", "glm-5.1", "glm-5.2", "hy3")),
    LONGCAT("LongCat（美团）", "https://api.longcat.chat/openai/v1", "LongCat-2.0", "国内官方",
            List.of("LongCat-2.0", "longcat-flash-chat")),

    SILICONFLOW("硅基流动 SiliconFlow", "https://api.siliconflow.cn/v1", "deepseek-ai/DeepSeek-V4-Flash", "国内聚合",
            List.of("deepseek-ai/DeepSeek-V4-Flash", "deepseek-ai/DeepSeek-V4.1-Flash-1017",
                    "zai-org/GLM-5.3", "moonshotai/Kimi-K3", "MiniMaxAI/MiniMax-M3")),
    MODELSCOPE("魔搭 ModelScope", "https://api-inference.modelscope.cn/v1", "ZhipuAI/GLM-5.2", "国内聚合",
            List.of("ZhipuAI/GLM-5.2", "Qwen/Qwen3.8-27B", "deepseek-ai/DeepSeek-V4-Flash")),

    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-5.4", "海外 / 聚合",
            List.of("gpt-5.4", "gpt-5.4-mini", "gpt-5.6-sol", "gpt-6.1-sol")),
    ANTHROPIC("Anthropic Claude", "https://api.anthropic.com/v1", "claude-sonnet-5-5", "海外 / 聚合",
            List.of("claude-sonnet-5-5", "claude-opus-5-5", "claude-haiku-4-5-20251001", "claude-fable-5-1")),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.8-flash", "海外 / 聚合",
            List.of("gemini-3.8-flash", "gemini-3.8-flash-high", "gemini-4-argon")),
    XAI("xAI Grok", "https://api.x.ai/v1", "grok-4.7", "海外 / 聚合",
            List.of("grok-4.7", "grok-4.5")),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-6.1-sol", "海外 / 聚合",
            List.of("openai/gpt-6.1-sol", "anthropic/claude-sonnet-5-5", "google/gemini-3.8-flash",
                    "zai-org/glm-5.3", "moonshotai/kimi-k3", "deepseek/deepseek-v4.1-flash")),
    NVIDIA("NVIDIA NIM", "https://integrate.api.nvidia.com/v1", "moonshotai/kimi-k2.6", "海外 / 聚合",
            List.of("moonshotai/kimi-k2.6", "zai-org/glm-5.3", "deepseek-ai/deepseek-v4.1-flash")),
    GROQ("Groq", "https://api.groq.com/openai/v1", "openai/gpt-oss-120b", "海外 / 聚合",
            List.of("openai/gpt-oss-120b", "openai/gpt-oss-20b")),
    MISTRAL("Mistral AI", "https://api.mistral.ai/v1", "mistral-medium-3-5", "海外 / 聚合",
            List.of("mistral-medium-3-5", "mistral-small-2603")),

    CUSTOM("自定义（OpenAI 兼容）", "", "", "自定义", List.of());

    private final String label;
    private final String defaultBaseUrl;
    private final String defaultModel;
    private final String group;
    private final List<String> models;

    AiProviderType(String label, String defaultBaseUrl, String defaultModel, String group, List<String> models) {
        this.label = label;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
        this.group = group;
        this.models = List.copyOf(models);
        if (!this.models.isEmpty() && !this.models.contains(defaultModel)) {
            throw new IllegalArgumentException("默认模型不在模型列表中: " + name());
        }
    }

    public String label() {
        return label;
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String defaultModel() {
        return defaultModel;
    }

    public String group() {
        return group;
    }

    public List<String> models() {
        return models;
    }

    public static AiProviderType from(String value) {
        if (value == null || value.isBlank()) {
            return CUSTOM;
        }
        try {
            return AiProviderType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return CUSTOM;
        }
    }
}
