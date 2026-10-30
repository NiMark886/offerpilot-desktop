package com.offerpilot.interview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.interview.domain.BlueprintQuestion;
import com.offerpilot.interview.domain.BlueprintRequest;
import com.offerpilot.interview.domain.BlueprintSection;
import com.offerpilot.interview.domain.InterviewBlueprint;
import com.offerpilot.questionbank.web.dto.QuestionBankItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MockBlueprintGenerator implements InterviewBlueprintGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public InterviewBlueprint generate(BlueprintRequest request) {
        int total = switch (request.durationMinutes()) {
            case 20 -> 6;
            case 45 -> 10;
            default -> 8;
        };

        String category = request.jobTemplate().getCategory() == null ? "" : request.jobTemplate().getCategory();
        boolean aiJob = category.contains("AI");

        Map<String, Integer> weights = weights(request.jobTemplate().getConfigJson(), aiJob);
        int basicCount = total >= 10 ? 4 : 3;
        int projectCount = total <= 6 ? 2 : 3;
        int scenarioCount = total >= 10 ? 2 : 1;
        int aiCount = total - basicCount - projectCount - scenarioCount;

        if (aiJob) {
            basicCount--;
            aiCount++;
        }

        List<BlueprintQuestion> basic = basicQuestions(
                category, request.retrievedQuestions(), experienceTarget(request.difficulty()));
        List<BlueprintQuestion> project = projectQuestions(request.resumeProfile());
        List<BlueprintQuestion> scenario = scenarioQuestions(category);
        List<BlueprintQuestion> ai = aiQuestions();

        List<BlueprintSection> sections = new ArrayList<>();
        addSection(sections, "BASIC", weights.getOrDefault("basic", 30), take(basic, basicCount));
        addSection(sections, "PROJECT", weights.getOrDefault("project", 35), take(project, projectCount));
        addSection(sections, "SCENARIO", weights.getOrDefault("scenario", 20), take(scenario, scenarioCount));
        if (aiCount > 0) {
            addSection(sections, "AI", weights.getOrDefault("ai", 15), take(ai, aiCount));
        }

        return new InterviewBlueprint(
                "你好，我是今天的 AI 面试官。我们会围绕岗位和你的简历进行一场结构化面试，请尽量用 STAR 结构回答。",
                sections,
                "今天的面试到这里，感谢你的时间。报告会在面试结束后生成。");
    }

    private void addSection(List<BlueprintSection> sections, String type, int weight,
                            List<BlueprintQuestion> questions) {
        if (!questions.isEmpty()) {
            sections.add(new BlueprintSection(type, weight, questions));
        }
    }

    private Map<String, Integer> weights(String configJson, boolean aiJob) {
        Map<String, Integer> defaults = new LinkedHashMap<>();
        defaults.put("basic", aiJob ? 20 : 30);
        defaults.put("project", aiJob ? 30 : 35);
        defaults.put("scenario", 20);
        defaults.put("ai", aiJob ? 30 : 15);
        if (configJson == null || configJson.isBlank()) {
            return defaults;
        }
        try {
            return objectMapper.readValue(configJson, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return defaults;
        }
    }

    private int experienceTarget(String experience) {
        if (experience == null) {
            return 2;
        }
        return switch (experience.toUpperCase(java.util.Locale.ROOT)) {
            case "CAMPUS", "EASY" -> 1;
            case "SENIOR", "HARD" -> 3;
            default -> 2;
        };
    }

    private List<BlueprintQuestion> take(List<BlueprintQuestion> source, int count) {
        List<BlueprintQuestion> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(source.get(i % source.size()));
        }
        return result;
    }

    private List<BlueprintQuestion> basicQuestions(String category, List<QuestionBankItem> retrieved,
                                                   int targetDifficulty) {
        List<BlueprintQuestion> result = new ArrayList<>(bankQuestions(retrieved, targetDifficulty));
        if (category.contains("AI")) {
            result.addAll(List.of(
                    question("请说明 RAG 的完整流程，以及每个阶段常见的失败点。", 2,
                            List.of("RAG", "检索"), List.of("文档切分", "Embedding", "召回", "重排", "生成"),
                            List.of("召回率低时你会怎么优化？")),
                    question("Embedding 和关键词检索各有什么优缺点？", 2,
                            List.of("Embedding", "检索"), List.of("语义匹配", "精确匹配", "混合检索"),
                            List.of("如何做混合检索？")),
                    question("如何设计一个稳定的 Prompt 模板？", 2,
                            List.of("提示词"), List.of("角色", "任务", "上下文", "输出格式", "约束"),
                            List.of("如何处理提示词注入？")),
                    question("如何评估一个 LLM 应用的效果？", 3,
                            List.of("评估"), List.of("离线评测", "人工评估", "线上指标", "A/B 测试"),
                            List.of("没有标注数据时怎么办？"))
            ));
            return dedupe(result);
        }
        if (category.contains("微服务")) {
            result.addAll(List.of(
                    question("Nacos 的服务注册与发现流程是怎样的？", 2,
                            List.of("Nacos"), List.of("注册", "心跳", "健康检查", "本地缓存"),
                            List.of("Nacos 挂了服务还能调用吗？")),
                    question("OpenFeign 的调用流程和超时、重试如何配置？", 2,
                            List.of("OpenFeign"), List.of("动态代理", "负载均衡", "超时", "重试"),
                            List.of("重试会带来什么问题？")),
                    question("Gateway 的核心能力有哪些？", 1,
                            List.of("Gateway"), List.of("路由", "断言", "过滤器", "限流"),
                            List.of("如何做灰度发布？")),
                    question("分布式事务有哪些常见方案？", 3,
                            List.of("分布式事务"), List.of("2PC", "TCC", "Saga", "本地消息表"),
                            List.of("你项目中用了哪种？为什么？"))
            ));
            return dedupe(result);
        }
        result.addAll(List.of(
                question("HashMap 在 JDK 8 中的扩容机制是怎样的？", 2,
                        List.of("集合", "HashMap"), List.of("数组+链表+红黑树", "扩容 2 倍", "链表转红黑树阈值 8"),
                        List.of("扩容后元素如何重新分布？")),
                question("线程池的核心参数有哪些？任务提交后如何处理？", 2,
                        List.of("并发", "线程池"), List.of("corePoolSize", "maximumPoolSize", "队列", "拒绝策略"),
                        List.of("队列满了会怎样？")),
                question("JVM 垃圾回收的分代模型和常见收集器？", 2,
                        List.of("JVM", "GC"), List.of("新生代", "老年代", "Minor GC", "Full GC", "G1"),
                        List.of("什么时候会触发 Full GC？")),
                question("MySQL 索引失效的常见场景有哪些？", 2,
                        List.of("MySQL", "索引"), List.of("最左前缀", "函数运算", "隐式转换", "范围查询"),
                        List.of("如何用 EXPLAIN 分析？"))
        ));
        return dedupe(result);
    }

    private List<BlueprintQuestion> bankQuestions(List<QuestionBankItem> retrieved, int targetDifficulty) {
        if (retrieved == null) {
            return List.of();
        }
        return retrieved.stream()
                .filter(item -> !"开放题".equals(item.category()))
                .filter(item -> !item.category().contains("经历"))
                .sorted(java.util.Comparator.comparingInt(
                        item -> Math.abs(item.difficulty() - targetDifficulty)))
                .map(item -> question(item.question(), item.difficulty(),
                        item.tags().stream().limit(3).toList(),
                        List.of(item.referenceAnswer()),
                        List.of("能再展开讲讲这道题的边界条件吗？")))
                .toList();
    }

    private List<BlueprintQuestion> dedupe(List<BlueprintQuestion> questions) {
        Map<String, BlueprintQuestion> map = new LinkedHashMap<>();
        for (BlueprintQuestion question : questions) {
            map.putIfAbsent(question.text(), question);
        }
        return new ArrayList<>(map.values());
    }

    private List<BlueprintQuestion> projectQuestions(com.offerpilot.resume.domain.ResumeProfile profile) {
        String projectName = profile != null && profile.projects() != null && !profile.projects().isEmpty()
                ? profile.projects().get(0).name()
                : "你简历中最有代表性的项目";
        List<BlueprintQuestion> questions = new ArrayList<>();
        questions.add(question("请介绍「" + projectName + "」的背景、目标和你的个人职责。", 2,
                List.of("项目经历"), List.of("背景", "目标", "职责", "结果"),
                List.of("这个项目最大的挑战是什么？")));
        questions.add(question("在「" + projectName + "」中，你做过最有技术含量的一个决策是什么？为什么？", 3,
                List.of("技术取舍"), List.of("方案对比", "取舍理由", "结果"),
                List.of("如果重来一次你会怎么改？")));
        questions.add(question("如果「" + projectName + "」的流量增长 10 倍，你会优先优化哪里？", 3,
                List.of("性能优化"), List.of("瓶颈分析", "缓存", "异步", "扩容"),
                List.of("如何验证优化效果？")));
        return questions;
    }

    private List<BlueprintQuestion> scenarioQuestions(String category) {
        if (category.contains("AI")) {
            return List.of(
                    question("线上 RAG 回答出现明显幻觉，你会如何定位和修复？", 3,
                            List.of("RAG", "幻觉"), List.of("检索质量", "引用校验", "Prompt 约束", "兜底策略"),
                            List.of("如何量化幻觉率？")),
                    question("设计一个支持多租户的 AI 问答服务，你会如何划分数据与额度？", 3,
                            List.of("系统设计"), List.of("租户隔离", "向量库分区", "额度", "限流"),
                            List.of("如何避免租户间数据泄漏？"))
            );
        }
        if (category.contains("微服务")) {
            return List.of(
                    question("线上某个微服务接口 P99 升高，你会如何排查？", 3,
                            List.of("故障排查"), List.of("链路追踪", "日志", "指标", "依赖"),
                            List.of("如何区分是自身问题还是下游问题？")),
                    question("设计一个高可用的服务注册与配置中心方案。", 3,
                            List.of("系统设计"), List.of("集群", "持久化", "本地缓存", "灰度"),
                            List.of("配置推送失败如何兜底？"))
            );
        }
        return List.of(
                question("线上接口 P99 从 200ms 涨到 2s，你会如何排查？", 3,
                        List.of("故障排查"), List.of("监控", "日志", "线程栈", "慢 SQL", "下游依赖"),
                        List.of("如何快速定位是哪个依赖变慢？")),
                question("设计一个支持 10 万 QPS 的短链接系统。", 3,
                        List.of("系统设计"), List.of("发号器", "缓存", "分库分表", "限流"),
                        List.of("如何保证短码不冲突？"))
        );
    }

    private List<BlueprintQuestion> aiQuestions() {
        return List.of(
                question("如何评估 RAG 的召回质量与最终答案质量？", 3,
                        List.of("RAG", "评估"), List.of("召回率", "命中率", "答案相关性", "人工评测"),
                        List.of("如何构造评测集？")),
                question("如何控制 LLM 应用的 token 成本？", 2,
                        List.of("成本控制"), List.of("上下文压缩", "缓存", "模型分级", "限流"),
                        List.of("哪些场景适合用小模型？")),
                question("如何防止提示词注入和数据泄漏？", 2,
                        List.of("安全"), List.of("输入隔离", "输出审核", "权限控制", "脱敏"),
                        List.of("简历中的恶意文本如何处理？"))
        );
    }

    private BlueprintQuestion question(String text, int difficulty, List<String> tags,
                                       List<String> referencePoints, List<String> followUps) {
        return new BlueprintQuestion(text, difficulty, tags, referencePoints, followUps);
    }
}
