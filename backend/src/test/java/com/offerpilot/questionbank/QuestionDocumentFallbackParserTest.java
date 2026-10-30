package com.offerpilot.questionbank;

import com.offerpilot.questionbank.service.QuestionDocumentFallbackParser;
import com.offerpilot.questionbank.service.QuestionImportDefaults;
import com.offerpilot.questionbank.web.dto.QuestionBankImportItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionDocumentFallbackParserTest {

    private final QuestionDocumentFallbackParser parser = new QuestionDocumentFallbackParser();
    private final QuestionImportDefaults defaults =
            new QuestionImportDefaults("Java 基础", 2, "文档导入 · test.txt");

    @Test
    void parsesNumberedQuestionsWithAnswersAndMultiLineAnswers() {
        List<QuestionBankImportItem> items = parser.parse("""
                1. HashMap 的底层结构是什么？
                答：数组 + 链表 + 红黑树。
                链表过长时会转成红黑树。
                2、线程池的核心参数有哪些？
                A: corePoolSize、maximumPoolSize、keepAliveTime、workQueue、threadFactory、handler。
                3）什么是死锁？
                答：多个线程互相等待对方释放锁。
                """, defaults);

        assertThat(items).hasSize(3);
        assertThat(items.get(0).question()).contains("HashMap");
        assertThat(items.get(0).referenceAnswer()).contains("红黑树", "链表过长");
        assertThat(items.get(1).question()).contains("线程池");
        assertThat(items.get(1).referenceAnswer()).contains("corePoolSize");
        assertThat(items.get(2).question()).contains("死锁");
        assertThat(items.get(0).category()).isEqualTo("Java 基础");
        assertThat(items.get(0).difficulty()).isEqualTo(2);
        assertThat(items.get(0).tags()).contains("Java 基础", "文档导入");
    }

    @Test
    void parsesInlineQuestionAndAnswer() {
        List<QuestionBankImportItem> items = parser.parse(
                "Q1: 什么是 JVM？ A: JVM 是 Java 虚拟机。", defaults);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).question()).isEqualTo("什么是 JVM？");
        assertThat(items.get(0).referenceAnswer()).isEqualTo("JVM 是 Java 虚拟机。");
    }

    @Test
    void fallsBackToParagraphsWhenThereAreNoNumberedQuestions() {
        List<QuestionBankImportItem> items = parser.parse("""
                HashMap 的底层结构是什么？
                数组 + 链表 + 红黑树。

                线程池的核心参数有哪些？
                corePoolSize、maximumPoolSize、keepAliveTime。
                """, defaults);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).referenceAnswer()).contains("红黑树");
        assertThat(items.get(1).referenceAnswer()).contains("corePoolSize");
    }

    @Test
    void keepsQuestionWhenTheDocumentHasNoAnswer() {
        List<QuestionBankImportItem> items = parser.parse("1. 什么情况下会发生内存泄漏？", defaults);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).question()).contains("内存泄漏");
        assertThat(items.get(0).referenceAnswer()).isEmpty();
    }
}
