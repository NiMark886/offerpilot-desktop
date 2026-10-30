package com.offerpilot.questionbank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.questionbank.service.QuestionDocumentJsonParser;
import com.offerpilot.questionbank.service.QuestionImportDefaults;
import com.offerpilot.questionbank.web.dto.QuestionBankImportItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionDocumentJsonParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QuestionDocumentJsonParser parser = new QuestionDocumentJsonParser(objectMapper);
    private final QuestionImportDefaults defaults =
            new QuestionImportDefaults("Java 基础", 1, "文档导入 · questions.pdf");

    @Test
    void parsesCodeFencedJsonAndNormalizesFields() {
        List<QuestionBankImportItem> items = parser.parse("""
                ```json
                {
                  "questions": [
                    {
                      "question": "1. HashMap 为什么查询快？",
                      "answer": "通过哈希定位数组下标。",
                      "category": "Java 集合",
                      "difficulty": "中等",
                      "tags": "Java, 集合"
                    },
                    {
                      "question": "HashMap 为什么查询快？",
                      "referenceAnswer": "重复题应被去掉。"
                    }
                  ]
                }
                ```
                """, defaults);

        assertThat(items).hasSize(1);
        QuestionBankImportItem item = items.get(0);
        assertThat(item.question()).isEqualTo("HashMap 为什么查询快？");
        assertThat(item.referenceAnswer()).contains("哈希");
        assertThat(item.category()).isEqualTo("Java 集合");
        assertThat(item.difficulty()).isEqualTo(2);
        assertThat(item.tags()).contains("Java", "集合");
        assertThat(item.source()).isEqualTo("文档导入 · questions.pdf");
    }

    @Test
    void acceptsTopLevelArrayAndFillsDefaults() {
        List<QuestionBankImportItem> items = parser.parse("""
                [
                  {"content":"Redis 为什么快？","参考答案":"基于内存、单线程模型、IO 多路复用。"}
                ]
                """, defaults);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).category()).isEqualTo("Java 基础");
        assertThat(items.get(0).difficulty()).isEqualTo(1);
        assertThat(items.get(0).tags()).contains("Java 基础", "文档导入");
    }
}
