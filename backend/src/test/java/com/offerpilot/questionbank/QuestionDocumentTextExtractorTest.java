package com.offerpilot.questionbank;

import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.questionbank.service.QuestionDocumentTextExtractor;
import com.offerpilot.resume.TestFiles;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionDocumentTextExtractorTest {

    private final QuestionDocumentTextExtractor extractor = new QuestionDocumentTextExtractor();

    @Test
    void extractsUtf8TextDocument() {
        String text = extractor.extract(
                "1. 什么是 JVM？\n答：Java 虚拟机。".getBytes(StandardCharsets.UTF_8), "questions.txt");

        assertThat(text).contains("什么是 JVM", "Java 虚拟机");
    }

    @Test
    void extractsGbkTextDocument() {
        String text = extractor.extract(
                "1. 什么是 JVM？".getBytes(Charset.forName("GB18030")), "questions.txt");

        assertThat(text).contains("什么是 JVM");
    }

    @Test
    void extractsDocxDocument() throws Exception {
        byte[] docx = TestFiles.docx("1. 线程池的核心参数？\n答：corePoolSize 等。");

        String text = extractor.extract(docx, "questions.docx");

        assertThat(text).contains("线程池", "corePoolSize");
    }

    @Test
    void rejectsUnsupportedFormat() {
        assertThatThrownBy(() -> extractor.extract("test".getBytes(StandardCharsets.UTF_8), "questions.doc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF、DOCX、TXT、Markdown");
    }
}
