package com.offerpilot.interview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.interview.domain.AnswerEvaluation;
import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.InterviewSession;
import com.offerpilot.interview.web.dto.InterviewReportResult;
import com.offerpilot.track.domain.RoleProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class MockInterviewReportGenerator implements InterviewReportGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public InterviewReportResult generate(InterviewSession session,
                                           List<InterviewQuestion> questions,
                                           List<InterviewMessage> messages,
                                           List<AnswerEvaluation> evaluations,
                                           RoleProfile profile) {
        if (profile != null && profile.isGeneral()) {
            return generateSales(questions, messages, evaluations);
        }
        Map<String, Integer> empty = emptyDimensions();
        if (evaluations == null || evaluations.isEmpty()) {
            return new InterviewReportResult(empty, 0,
                    "本场面试没有有效回答，无法生成评分报告。",
                    List.of(), List.of("没有有效回答"),
                    List.of("建议重新完成一场面试，至少回答 3 道题。"));
        }

        List<InterviewMessage> answers = messages.stream()
                .filter(message -> "CANDIDATE".equals(message.getRole()))
                .toList();
        Map<Long, String> typeByQuestion = questions.stream()
                .collect(Collectors.toMap(InterviewQuestion::getId, InterviewQuestion::getType, (a, b) -> a));

        double avgScore = evaluations.stream()
                .mapToInt(evaluation -> evaluation.getScore() == null ? 0 : evaluation.getScore())
                .average().orElse(0);
        double avgLength = answers.stream()
                .mapToInt(message -> message.getContent() == null ? 0 : message.getContent().length())
                .average().orElse(0);

        int accuracy = percent(avgScore);
        int depth = clamp((int) Math.round(accuracy * 0.75 + Math.min(20, avgLength / 15.0)));

        double projectAvg = evaluations.stream()
                .filter(evaluation -> "PROJECT".equals(typeByQuestion.get(evaluation.getQuestionId())))
                .mapToInt(evaluation -> evaluation.getScore() == null ? 0 : evaluation.getScore())
                .average().orElse(avgScore);
        int project = percent(projectAvg);

        String joinedAnswers = answers.stream()
                .map(InterviewMessage::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        int markers = 0;
        for (String marker : List.of("首先", "然后", "最后", "因为", "所以", "例如", "第一", "第二")) {
            if (joinedAnswers.contains(marker)) {
                markers++;
            }
        }
        int expression = joinedAnswers.isBlank()
                ? 0
                : clamp((int) Math.round(55 + markers * 5 + Math.min(20, avgLength / 8.0)));

        int match = clamp((int) Math.round(accuracy * 0.4 + depth * 0.35 + project * 0.25));
        int overall = clamp((int) Math.round(
                accuracy * 0.30 + depth * 0.25 + project * 0.20 + expression * 0.15 + match * 0.10));

        long answeredQuestions = evaluations.stream()
                .map(AnswerEvaluation::getQuestionId)
                .distinct()
                .count();

        String summary = "本场面试共完成 %d 道题，整体得分 %d 分。技术准确性 %d 分，技术深度 %d 分，项目表现 %d 分，表达逻辑 %d 分，岗位匹配度 %d 分。"
                .formatted(answeredQuestions, overall, accuracy, depth, project, expression, match);

        List<String> strengths = new ArrayList<>();
        if (accuracy >= 75) strengths.add("技术准确性较好");
        if (depth >= 75) strengths.add("回答有一定技术深度");
        if (project >= 75) strengths.add("项目经历表达清晰");
        if (expression >= 75) strengths.add("表达逻辑清晰");
        if (match >= 75) strengths.add("与目标岗位匹配度较高");
        if (strengths.isEmpty()) strengths.add("完成了本场面试，具备继续训练的基础。");

        List<String> weaknesses = new ArrayList<>();
        if (accuracy < 60) weaknesses.add("技术准确性不足");
        if (depth < 60) weaknesses.add("回答深度不够");
        if (project < 60) weaknesses.add("项目经历讲得不够清楚");
        if (expression < 60) weaknesses.add("表达逻辑需要加强");
        if (match < 60) weaknesses.add("岗位匹配度偏低");

        Map<String, Long> missingCounts = evaluations.stream()
                .flatMap(evaluation -> readList(evaluation.getMissingPoints()).stream())
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()));
        missingCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .forEach(entry -> weaknesses.add("知识点缺口：" + entry.getKey()));
        if (weaknesses.isEmpty()) {
            weaknesses.add("暂无明显短板，建议继续提升回答深度和项目细节。");
        }

        List<String> suggestions = new ArrayList<>();
        if (accuracy < 60) suggestions.add("先补齐高频八股，再做下一场模拟面试。");
        if (depth < 60) suggestions.add("回答时补充原理、取舍和边界条件，避免只讲结论。");
        if (project < 60) suggestions.add("用 STAR 结构重写项目经历，突出个人贡献和量化结果。");
        if (expression < 60) suggestions.add("练习“总-分-总”表达，先给结论再展开。");
        if (suggestions.isEmpty()) suggestions.add("保持训练节奏，下一场可以挑战更高难度。");

        return new InterviewReportResult(
                dimensions(accuracy, depth, project, expression, match),
                overall, summary, strengths, weaknesses, suggestions);
    }

    private InterviewReportResult generateSales(List<InterviewQuestion> questions,
                                                List<InterviewMessage> messages,
                                                List<AnswerEvaluation> evaluations) {
        Map<String, Integer> empty = emptySalesDimensions();
        if (evaluations == null || evaluations.isEmpty()) {
            return new InterviewReportResult(empty, 0,
                    "本场面试没有有效回答，无法生成销售能力评分报告。",
                    List.of(), List.of("没有有效回答"),
                    List.of("建议重新完成一场面试，至少回答 3 道销售题。"));
        }

        List<InterviewMessage> answers = messages.stream()
                .filter(message -> "CANDIDATE".equals(message.getRole()))
                .toList();
        double avgScore = evaluations.stream()
                .mapToInt(evaluation -> evaluation.getScore() == null ? 0 : evaluation.getScore())
                .average().orElse(0);
        double avgLength = answers.stream()
                .mapToInt(message -> message.getContent() == null ? 0 : message.getContent().length())
                .average().orElse(0);
        String joined = answers.stream()
                .map(InterviewMessage::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));

        int base = percent(avgScore);
        int markers = 0;
        for (String marker : List.of("首先", "然后", "最后", "因为", "所以", "例如", "第一", "第二")) {
            if (joined.contains(marker)) {
                markers++;
            }
        }
        int communication = joined.isBlank()
                ? 0
                : clamp((int) Math.round(50 + markers * 5 + Math.min(25, avgLength / 8.0)));
        int customerInsight = clamp((int) Math.round(
                base * 0.65 + keywordHits(joined, List.of(
                        "客户", "需求", "决策", "预算", "痛点", "关键人")) * 5 + 10));
        int resilience = clamp((int) Math.round(
                base * 0.55 + keywordHits(joined, List.of(
                        "拒绝", "异议", "投诉", "压力", "失败", "复盘", "调整")) * 5 + 20));
        int numericEvidence = countNumericEvidence(joined);
        int resultOrientation = clamp(base + Math.min(15, numericEvidence * 4));
        int jobMatch = clamp((int) Math.round(
                customerInsight * 0.30 + communication * 0.25
                        + resilience * 0.20 + resultOrientation * 0.25));
        int overall = clamp((int) Math.round(
                customerInsight * 0.25 + communication * 0.20
                        + resilience * 0.20 + resultOrientation * 0.25 + jobMatch * 0.10));

        long answeredQuestions = evaluations.stream()
                .map(AnswerEvaluation::getQuestionId)
                .distinct()
                .count();
        String summary = "本场销售面试共完成 %d 道题，整体得分 %d 分。客户洞察 %d 分，沟通说服 %d 分，抗压应变 %d 分，结果导向 %d 分，岗位匹配 %d 分。"
                .formatted(answeredQuestions, overall, customerInsight, communication,
                        resilience, resultOrientation, jobMatch);

        List<String> strengths = new ArrayList<>();
        if (customerInsight >= 75) strengths.add("能围绕客户需求和决策链展开");
        if (communication >= 75) strengths.add("表达有结构、有说服力");
        if (resilience >= 75) strengths.add("面对拒绝和压力能保持稳定");
        if (resultOrientation >= 75) strengths.add("回答中有明确结果和量化证据");
        if (jobMatch >= 75) strengths.add("销售岗位匹配度较高");
        if (strengths.isEmpty()) strengths.add("完成了本场销售面试，具备继续训练的基础。");

        List<String> weaknesses = new ArrayList<>();
        if (customerInsight < 60) weaknesses.add("客户洞察不足，容易停留在产品介绍");
        if (communication < 60) weaknesses.add("表达结构不够清晰");
        if (resilience < 60) weaknesses.add("抗压和应变案例不够充分");
        if (resultOrientation < 60) weaknesses.add("结果量化不足");
        if (jobMatch < 60) weaknesses.add("岗位匹配度偏低");

        Map<String, Long> missingCounts = evaluations.stream()
                .flatMap(evaluation -> readList(evaluation.getMissingPoints()).stream())
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()));
        missingCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .forEach(entry -> weaknesses.add("能力缺口：" + entry.getKey()));
        if (weaknesses.isEmpty()) {
            weaknesses.add("暂无明显短板，建议继续提升复杂客户场景的处理能力。");
        }

        List<String> suggestions = new ArrayList<>();
        if (customerInsight < 60) suggestions.add("练习从客户业务目标、痛点、预算和决策链四个角度提问。");
        if (communication < 60) suggestions.add("用“结论-依据-案例-结果”的结构回答。");
        if (resilience < 60) suggestions.add("准备 2-3 个被拒绝或处理异议的真实案例，讲清你的调整动作。");
        if (resultOrientation < 60) suggestions.add("给每个案例补充数字，比如签约金额、转化率、回款周期。");
        if (jobMatch < 60) suggestions.add("结合目标行业补充客户画像和竞品差异。");
        if (suggestions.isEmpty()) suggestions.add("保持训练节奏，下一场可以挑战更复杂的客户场景。");

        return new InterviewReportResult(
                salesDimensions(customerInsight, communication, resilience, resultOrientation, jobMatch),
                overall, summary, strengths, weaknesses, suggestions);
    }

    private Map<String, Integer> emptySalesDimensions() {
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("customerInsight", 0);
        dimensions.put("communication", 0);
        dimensions.put("resilience", 0);
        dimensions.put("resultOrientation", 0);
        dimensions.put("jobMatch", 0);
        return dimensions;
    }

    private Map<String, Integer> salesDimensions(int customerInsight, int communication,
                                                 int resilience, int resultOrientation, int jobMatch) {
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("customerInsight", customerInsight);
        dimensions.put("communication", communication);
        dimensions.put("resilience", resilience);
        dimensions.put("resultOrientation", resultOrientation);
        dimensions.put("jobMatch", jobMatch);
        return dimensions;
    }

    private int keywordHits(String text, List<String> keywords) {
        int hits = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                hits++;
            }
        }
        return hits;
    }

    private int countNumericEvidence(String text) {
        int digits = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                digits++;
            }
        }
        return Math.min(5, digits / 2);
    }

    private Map<String, Integer> emptyDimensions() {
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("accuracy", 0);
        dimensions.put("depth", 0);
        dimensions.put("project", 0);
        dimensions.put("expression", 0);
        dimensions.put("match", 0);
        return dimensions;
    }

    private Map<String, Integer> dimensions(int accuracy, int depth, int project, int expression, int match) {
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("accuracy", accuracy);
        dimensions.put("depth", depth);
        dimensions.put("project", project);
        dimensions.put("expression", expression);
        dimensions.put("match", match);
        return dimensions;
    }

    private int percent(double score) {
        return clamp((int) Math.round(score / 5.0 * 100));
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }
}
