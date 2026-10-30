# OfferPilot P0 Week 2 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现简历上传/解析、面试创建和面试蓝图生成：用户上传 PDF/DOCX 简历后，系统抽取文本与画像，选择岗位与参数创建一场面试，并生成可预览的面试题目蓝图。

**Architecture:** 在既有模块化单体上新增 `resume` 与 `interview` 两个业务域。简历文件先落本地存储（`FileStorage` 接口，后续可替换为 MinIO），文本抽取使用 PDFBox/POI，画像解析默认走规则解析，`app.ai.enabled=true` 时优先调用 Spring AI + DeepSeek，失败自动回退规则解析。面试蓝图默认由确定性 Mock 生成器产出，开启 AI 后由 LLM 生成，失败回退 Mock；两者都写入 `interview_session` 与 `interview_question`。

**Tech Stack:** Java 21、Spring Boot 3.5.16、Spring AI 1.1.8（OpenAI 兼容接入 DeepSeek）、PDFBox 3.0.8、Apache POI 5.4.1、MyBatis-Plus 3.5.18、H2/MySQL、Vue3 + Element Plus。

---

## 0. 范围与完成定义

**本计划覆盖 P0 第 2 周（W2）：**

- 简历上传（PDF/DOCX）、文本抽取、结构化画像解析、简历列表/详情/删除。
- 创建面试：选择岗位模板、简历、难度、时长、面试官风格。
- 面试蓝图：默认 Mock 生成，支持 Spring AI + DeepSeek 生成并失败回退。
- 面试详情：返回岗位、参数、题目列表（题型/难度/考察标签/参考要点）。
- 前端：面试创建弹窗（含简历上传）、面试预览页。
- 后端测试、前端构建、浏览器端到端验收通过。

**本计划不做：**

- 多轮对话、SSE、追问/提示/跳过（W3）。
- 单题评分、五维报告、历史成长曲线（W4）。
- RAG 题库检索、额度限流、运营后台（W5）。
- MinIO 接入：W2 用本地文件存储，部署阶段再替换。
- 真实 AI 调用不是默认路径：默认 `app.ai.enabled=false` 使用 Mock，配置 `DEEPSEEK_API_KEY` 与 `APP_AI_ENABLED=true` 后启用。

**完成定义：**

- `mvn -f backend/pom.xml test` 全部通过（包含 W1 的 11 个测试 + W2 新增测试）。
- `npm --prefix frontend run build` 通过。
- 浏览器端：注册 → 上传 DOCX 简历 → 创建面试 → 跳转面试预览页 → 看到 8 道题。
- 工作区干净，提交到 `feat/p0-week2` 分支。

**分支策略：** W1 的 PR 尚未合并，因此从当前 `feat/p0-week1` 新建 `feat/p0-week2`。W1 合并后可将 W2 rebase 到 `main`。

---

## 1. 文件结构（W2 新增/修改）

```text
backend/
├── pom.xml                                      # 修改：Spring AI、PDFBox、POI
├── src/main/java/com/offerpilot/
│   ├── common/
│   │   ├── exception/ErrorCode.java             # 修改：NOT_FOUND、FILE_TOO_LARGE
│   │   └── exception/GlobalExceptionHandler.java# 修改：404/413/上传超限
│   ├── resume/
│   │   ├── domain/Resume.java
│   │   ├── domain/ResumeProfile.java
│   │   ├── domain/ProjectExperience.java
│   │   ├── mapper/ResumeMapper.java
│   │   ├── service/ResumeService.java
│   │   ├── service/ResumeProfileService.java
│   │   ├── service/HeuristicResumeProfileParser.java
│   │   ├── service/AiResumeProfileParser.java   # 条件启用
│   │   ├── service/ResumeTextExtractor.java
│   │   ├── storage/FileStorage.java
│   │   ├── storage/LocalFileStorage.java
│   │   ├── storage/StoredFile.java
│   │   ├── storage/StorageConfig.java
│   │   ├── web/ResumeController.java
│   │   └── web/dto/ResumeView.java
│   └── interview/
│       ├── domain/InterviewSession.java
│       ├── domain/InterviewQuestion.java
│       ├── domain/InterviewBlueprint.java
│       ├── domain/BlueprintSection.java
│       ├── domain/BlueprintQuestion.java
│       ├── domain/BlueprintRequest.java
│       ├── mapper/InterviewSessionMapper.java
│       ├── mapper/InterviewQuestionMapper.java
│       ├── service/InterviewBlueprintGenerator.java
│       ├── service/MockBlueprintGenerator.java
│       ├── service/SpringAiBlueprintGenerator.java # 条件启用
│       ├── service/InterviewService.java
│       ├── web/InterviewController.java
│       └── web/dto/CreateInterviewRequest.java
│       └── web/dto/InterviewSessionView.java
│       └── web/dto/QuestionView.java
├── src/main/resources/
│   ├── application.yml                          # 修改：multipart、AI、storage
│   ├── application-test.yml                     # 修改：AI 关闭、测试上传目录
│   └── db/migration/V3__resume_and_interview.sql
└── src/test/java/com/offerpilot/
    ├── resume/ResumeTextExtractorTest.java
    ├── resume/HeuristicResumeProfileParserTest.java
    ├── resume/LocalFileStorageTest.java
    ├── resume/ResumeControllerIT.java
    ├── interview/MockBlueprintGeneratorTest.java
    └── interview/InterviewControllerIT.java

frontend/
├── src/api/resume.ts
├── src/api/interview.ts
├── src/components/InterviewSetupDialog.vue
├── src/views/InterviewSessionView.vue
├── src/views/HomeView.vue                       # 修改：接入创建弹窗
└── src/router/index.ts                          # 修改：新增 /interviews/:id
```

---

## Task 1: 分支、依赖与配置

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application-test.yml`
- Modify: `backend/src/main/java/com/offerpilot/common/exception/ErrorCode.java`
- Modify: `backend/src/main/java/com/offerpilot/common/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: 创建 W2 分支**

Run:
```bash
cd /e/work
git checkout -b feat/p0-week2
```

Expected: `Switched to a new branch 'feat/p0-week2'`。

- [ ] **Step 2: 在 `backend/pom.xml` 增加依赖管理**

在 `<properties>` 后、`<dependencies>` 前加入：
```xml
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>1.1.8</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
```

在 `<dependencies>` 中加入 Spring AI、PDFBox、POI：
```xml
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-openai</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.pdfbox</groupId>
            <artifactId>pdfbox</artifactId>
            <version>3.0.8</version>
        </dependency>
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>5.4.1</version>
        </dependency>
```

- [ ] **Step 3: 修改 `application.yml`**

在 `spring:` 下增加：
```yaml
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 12MB
  ai:
    openai:
      base-url: https://api.deepseek.com
      api-key: ${DEEPSEEK_API_KEY:demo-key}
      chat:
        options:
          model: deepseek-chat
          temperature: 0.4
```

在 `app:` 下增加：
```yaml
  ai:
    enabled: ${APP_AI_ENABLED:false}
  storage:
    local-root: ${OFFERPILOT_UPLOAD_DIR:./data/uploads}
    max-size: 10485760
```

- [ ] **Step 4: 修改 `application-test.yml`**

在 `spring:` 下增加：
```yaml
  ai:
    openai:
      api-key: test-key
```

在文件末尾增加：
```yaml
app:
  ai:
    enabled: false
  storage:
    local-root: ./target/test-uploads
```

- [ ] **Step 5: 扩展 `ErrorCode`**

将 `ErrorCode` 改为：
```java
package com.offerpilot.common.exception;

public enum ErrorCode {
    VALIDATION_ERROR(40001, "参数校验失败"),
    UNAUTHORIZED(40101, "未登录或登录已过期"),
    FORBIDDEN(40301, "无权访问"),
    NOT_FOUND(40401, "资源不存在"),
    CONFLICT(40901, "资源冲突"),
    FILE_TOO_LARGE(41301, "文件过大"),
    INTERNAL_ERROR(50000, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
```

- [ ] **Step 6: 扩展 `GlobalExceptionHandler`**

在 `handleBusiness` 的 `switch` 中增加：
```java
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FILE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
```

在类中增加：
```java
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(ErrorCode.FILE_TOO_LARGE.code(), "简历文件不能超过 10MB"));
    }
```

导入：
```java
import org.springframework.web.multipart.MaxUploadSizeExceededException;
```

- [ ] **Step 7: 编译验证**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -DskipTests package
```

Expected: `BUILD SUCCESS`。首次会下载 Spring AI / PDFBox / POI 依赖。

- [ ] **Step 8: 提交**

Run:
```bash
cd /e/work
git add backend/pom.xml backend/src/main/resources/application.yml \
  backend/src/test/resources/application-test.yml \
  backend/src/main/java/com/offerpilot/common/exception/ErrorCode.java \
  backend/src/main/java/com/offerpilot/common/exception/GlobalExceptionHandler.java
git commit -m "chore(w2): add AI, PDF and DOCX dependencies"
```

---

## Task 2: V3 数据库迁移

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__resume_and_interview.sql`
- Test: `backend/src/test/java/com/offerpilot/resume/SchemaV3IT.java`

- [ ] **Step 1: 先写失败测试**

Create `backend/src/test/java/com/offerpilot/resume/SchemaV3IT.java`:
```java
package com.offerpilot.resume;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaV3IT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsW2Tables() {
        assertThat(tableCount("resume")).isEqualTo(1);
        assertThat(tableCount("interview_session")).isEqualTo(1);
        assertThat(tableCount("interview_question")).isEqualTo(1);
    }

    private int tableCount(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = ?",
                Integer.class, table);
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=SchemaV3IT test
```

Expected: FAIL，`resume` 表不存在或查询结果为 0。

- [ ] **Step 3: 创建 V3 迁移**

Create `backend/src/main/resources/db/migration/V3__resume_and_interview.sql`:
```sql
CREATE TABLE resume (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    content_type VARCHAR(128) NULL,
    file_size BIGINT NOT NULL,
    raw_text TEXT NULL,
    parsed_json TEXT NULL,
    parse_status VARCHAR(20) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE INDEX idx_resume_user_id ON resume(user_id);

CREATE TABLE interview_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    job_template_id BIGINT NOT NULL,
    resume_id BIGINT NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    duration_minutes INT NOT NULL,
    persona VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    blueprint_json TEXT NULL,
    total_score INT NULL,
    started_at DATETIME(3) NULL,
    ended_at DATETIME(3) NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE INDEX idx_interview_session_user_id ON interview_session(user_id);

CREATE TABLE interview_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    seq INT NOT NULL,
    type VARCHAR(20) NOT NULL,
    difficulty INT NOT NULL,
    content TEXT NOT NULL,
    knowledge_tags TEXT NULL,
    reference_points TEXT NULL,
    follow_up_hints TEXT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE INDEX idx_interview_question_session_id ON interview_question(session_id);
```

- [ ] **Step 4: 运行测试，确认通过**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=SchemaV3IT test
```

Expected: PASS。

- [ ] **Step 5: 提交**

Run:
```bash
cd /e/work
git add backend/src/main/resources/db/migration/V3__resume_and_interview.sql \
  backend/src/test/java/com/offerpilot/resume/SchemaV3IT.java
git commit -m "feat(db): add resume and interview tables"
```

---

## Task 3: 本地文件存储

**Files:**
- Create: `backend/src/main/java/com/offerpilot/resume/storage/StoredFile.java`
- Create: `backend/src/main/java/com/offerpilot/resume/storage/FileStorage.java`
- Create: `backend/src/main/java/com/offerpilot/resume/storage/LocalFileStorage.java`
- Create: `backend/src/main/java/com/offerpilot/resume/storage/StorageConfig.java`
- Test: `backend/src/test/java/com/offerpilot/resume/LocalFileStorageTest.java`

- [ ] **Step 1: 先写失败测试**

Create `backend/src/test/java/com/offerpilot/resume/LocalFileStorageTest.java`:
```java
package com.offerpilot.resume;

import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.resume.storage.LocalFileStorage;
import com.offerpilot.resume.storage.StoredFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAndDeletesFile() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(tempDir, 1024 * 1024);
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "hello".getBytes());

        StoredFile stored = storage.store(file, 7L);

        assertThat(stored.originalName()).isEqualTo("resume.pdf");
        assertThat(stored.size()).isEqualTo(5);
        assertThat(storage.resolve(stored.relativePath())).exists();

        storage.delete(stored.relativePath());
        assertThat(Files.exists(storage.resolve(stored.relativePath()))).isFalse();
    }

    @Test
    void rejectsUnsupportedExtension() {
        LocalFileStorage storage = new LocalFileStorage(tempDir, 1024 * 1024);
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> storage.store(file, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF");
    }

    @Test
    void rejectsOversizedFile() {
        LocalFileStorage storage = new LocalFileStorage(tempDir, 2);
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "hello".getBytes());

        assertThatThrownBy(() -> storage.store(file, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("简历文件过大");
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=LocalFileStorageTest test
```

Expected: 编译失败，`LocalFileStorage` 不存在。

- [ ] **Step 3: 创建 `StoredFile` 与 `FileStorage`**

Create `backend/src/main/java/com/offerpilot/resume/storage/StoredFile.java`:
```java
package com.offerpilot.resume.storage;

public record StoredFile(
        String relativePath,
        String originalName,
        String contentType,
        long size
) {
}
```

Create `backend/src/main/java/com/offerpilot/resume/storage/FileStorage.java`:
```java
package com.offerpilot.resume.storage;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileStorage {

    StoredFile store(MultipartFile file, Long userId);

    Path resolve(String relativePath);

    void delete(String relativePath);
}
```

- [ ] **Step 4: 创建 `LocalFileStorage`**

Create `backend/src/main/java/com/offerpilot/resume/storage/LocalFileStorage.java`:
```java
package com.offerpilot.resume.storage;

import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

public class LocalFileStorage implements FileStorage {

    private final Path root;
    private final long maxSize;

    public LocalFileStorage(Path root, long maxSize) {
        this.root = root.toAbsolutePath().normalize();
        this.maxSize = maxSize;
    }

    @Override
    public StoredFile store(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择要上传的简历文件");
        }
        if (file.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "简历文件过大");
        }

        String originalName = file.getOriginalFilename() == null ? "resume" : file.getOriginalFilename();
        String lower = originalName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".pdf") && !lower.endsWith(".docx")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "仅支持 PDF 或 DOCX 简历");
        }

        String extension = lower.substring(lower.lastIndexOf('.'));
        String relativePath = userId + "/" + UUID.randomUUID() + extension;

        try {
            Path target = root.resolve(relativePath).normalize();
            if (!target.startsWith(root)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法的文件路径");
            }
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            return new StoredFile(relativePath, originalName, file.getContentType(), file.getSize());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "简历保存失败");
        }
    }

    @Override
    public Path resolve(String relativePath) {
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法的文件路径");
        }
        return path;
    }

    @Override
    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException ignored) {
            // 删除失败不影响主流程
        }
    }
}
```

- [ ] **Step 5: 创建 `StorageConfig`**

Create `backend/src/main/java/com/offerpilot/resume/storage/StorageConfig.java`:
```java
package com.offerpilot.resume.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class StorageConfig {

    @Bean
    public FileStorage fileStorage(
            @Value("${app.storage.local-root:./data/uploads}") String localRoot,
            @Value("${app.storage.max-size:10485760}") long maxSize) {
        return new LocalFileStorage(Path.of(localRoot), maxSize);
    }
}
```

- [ ] **Step 6: 运行测试，确认通过**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=LocalFileStorageTest test
```

Expected: PASS。

- [ ] **Step 7: 提交**

Run:
```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/resume/storage \
  backend/src/test/java/com/offerpilot/resume/LocalFileStorageTest.java
git commit -m "feat(resume): add local file storage"
```

---

## Task 4: 简历文本抽取（PDF/DOCX）

**Files:**
- Create: `backend/src/main/java/com/offerpilot/resume/service/ResumeTextExtractor.java`
- Test: `backend/src/test/java/com/offerpilot/resume/ResumeTextExtractorTest.java`

- [ ] **Step 1: 先写失败测试**

Create `backend/src/test/java/com/offerpilot/resume/ResumeTextExtractorTest.java`:
```java
package com.offerpilot.resume;

import com.offerpilot.resume.service.ResumeTextExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeTextExtractorTest {

    private final ResumeTextExtractor extractor = new ResumeTextExtractor();

    @Test
    void extractsTextFromPdf() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("Java Spring Boot MySQL");
                content.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            pdf = out.toByteArray();
        }

        String text = extractor.extract(pdf, "resume.pdf");

        assertThat(text).contains("Java", "Spring Boot", "MySQL");
    }

    @Test
    void extractsTextFromDocx() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("Java Spring Boot MySQL");
            document.write(out);
            docx = out.toByteArray();
        }

        String text = extractor.extract(docx, "resume.docx");

        assertThat(text).contains("Java", "Spring Boot", "MySQL");
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=ResumeTextExtractorTest test
```

Expected: 编译失败，`ResumeTextExtractor` 不存在。

- [ ] **Step 3: 创建 `ResumeTextExtractor`**

Create `backend/src/main/java/com/offerpilot/resume/service/ResumeTextExtractor.java`:
```java
package com.offerpilot.resume.service;

import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

@Component
public class ResumeTextExtractor {

    public String extract(byte[] content, String fileName) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "简历文件为空");
        }

        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        try {
            String text = lower.endsWith(".pdf") ? extractPdf(content) : extractDocx(content);
            if (text == null || text.isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无法从简历中提取文本");
            }
            return text;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "简历解析失败，请确认文件格式");
        }
    }

    private String extractPdf(byte[] content) throws IOException {
        try (PDDocument document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocx(byte[] content) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=ResumeTextExtractorTest test
```

Expected: PASS。如果 PDFBox 3 的 `Loader` 导入报错，确认使用的是 `org.apache.pdfbox.Loader` 而不是 `PDDocument.load`。

- [ ] **Step 5: 提交**

Run:
```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/resume/service/ResumeTextExtractor.java \
  backend/src/test/java/com/offerpilot/resume/ResumeTextExtractorTest.java
git commit -m "feat(resume): extract text from PDF and DOCX"
```

---

## Task 5: 简历画像解析（规则 + 可选 AI）

**Files:**
- Create: `backend/src/main/java/com/offerpilot/resume/domain/ResumeProfile.java`
- Create: `backend/src/main/java/com/offerpilot/resume/domain/ProjectExperience.java`
- Create: `backend/src/main/java/com/offerpilot/resume/service/HeuristicResumeProfileParser.java`
- Create: `backend/src/main/java/com/offerpilot/resume/service/AiResumeProfileParser.java`
- Create: `backend/src/main/java/com/offerpilot/resume/service/ResumeProfileService.java`
- Test: `backend/src/test/java/com/offerpilot/resume/HeuristicResumeProfileParserTest.java`

- [ ] **Step 1: 先写失败测试**

Create `backend/src/test/java/com/offerpilot/resume/HeuristicResumeProfileParserTest.java`:
```java
package com.offerpilot.resume;

import com.offerpilot.resume.domain.ResumeProfile;
import com.offerpilot.resume.service.HeuristicResumeProfileParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicResumeProfileParserTest {

    private final HeuristicResumeProfileParser parser = new HeuristicResumeProfileParser();

    @Test
    void parsesBasicProfile() {
        String text = """
                张三
                邮箱：zhangsan@example.com
                手机：13800138000
                3 年 Java 后端开发经验
                技能：Java, Spring Boot, MySQL, Redis, MyBatis
                项目：中州养老智能物联网平台
                负责设备数据采集、报警规则与 AI 健康评估集成。
                """;

        ResumeProfile profile = parser.parse(text);

        assertThat(profile.name()).isEqualTo("张三");
        assertThat(profile.email()).isEqualTo("zhangsan@example.com");
        assertThat(profile.phone()).isEqualTo("13800138000");
        assertThat(profile.yearsOfExperience()).isEqualTo(3);
        assertThat(profile.skills()).contains("Java", "Spring Boot", "MySQL", "Redis", "MyBatis");
        assertThat(profile.projects()).isNotEmpty();
        assertThat(profile.projects().get(0).name()).contains("中州养老");
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=HeuristicResumeProfileParserTest test
```

Expected: 编译失败，`ResumeProfile` / `HeuristicResumeProfileParser` 不存在。

- [ ] **Step 3: 创建 `ResumeProfile` 与 `ProjectExperience`**

Create `backend/src/main/java/com/offerpilot/resume/domain/ProjectExperience.java`:
```java
package com.offerpilot.resume.domain;

public record ProjectExperience(String name, String description) {
}
```

Create `backend/src/main/java/com/offerpilot/resume/domain/ResumeProfile.java`:
```java
package com.offerpilot.resume.domain;

import java.util.List;

public record ResumeProfile(
        String name,
        String email,
        String phone,
        Integer yearsOfExperience,
        List<String> skills,
        List<ProjectExperience> projects,
        String education,
        String summary
) {
}
```

- [ ] **Step 4: 创建 `HeuristicResumeProfileParser`**

Create `backend/src/main/java/com/offerpilot/resume/service/HeuristicResumeProfileParser.java`:
```java
package com.offerpilot.resume.service;

import com.offerpilot.resume.domain.ProjectExperience;
import com.offerpilot.resume.domain.ResumeProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HeuristicResumeProfileParser {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern YEARS = Pattern.compile("(\\d+)\\s*(年|years?)", Pattern.CASE_INSENSITIVE);

    private static final List<String> KNOWN_SKILLS = List.of(
            "Java", "Spring Boot", "Spring Cloud", "Spring", "MyBatis", "MySQL", "Redis",
            "JVM", "并发", "微服务", "Docker", "Kubernetes", "Linux", "RabbitMQ", "Kafka",
            "RAG", "Agent", "大模型", "提示词", "向量数据库", "Embedding", "Python"
    );

    public ResumeProfile parse(String rawText) {
        String text = rawText == null ? "" : rawText.replace("\r\n", "\n");
        String email = findFirst(EMAIL, text);
        String phone = findFirst(PHONE, text);
        Integer years = findYears(text);
        List<String> skills = findSkills(text);
        List<ProjectExperience> projects = findProjects(text);
        String education = findEducation(text);
        String name = findName(text);
        String summary = text.length() > 220 ? text.substring(0, 220).trim() : text.trim();

        return new ResumeProfile(name, email, phone, years, skills, projects, education, summary);
    }

    private String findFirst(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private Integer findYears(String text) {
        Matcher matcher = YEARS.matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private List<String> findSkills(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> skills = new LinkedHashSet<>();
        for (String skill : KNOWN_SKILLS) {
            if (lower.contains(skill.toLowerCase(Locale.ROOT))) {
                skills.add(skill);
            }
        }
        return new ArrayList<>(skills);
    }

    private List<ProjectExperience> findProjects(String text) {
        List<ProjectExperience> projects = new ArrayList<>();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.contains("项目") && trimmed.length() >= 6) {
                String name = trimmed;
                int separator = Math.max(trimmed.indexOf('：'), trimmed.indexOf(':'));
                if (separator > 0 && separator < trimmed.length() - 1) {
                    name = trimmed.substring(separator + 1).trim();
                }
                if (name.length() > 40) {
                    name = name.substring(0, 40);
                }
                projects.add(new ProjectExperience(name, trimmed));
                if (projects.size() >= 3) {
                    break;
                }
            }
        }
        return projects;
    }

    private String findEducation(String text) {
        for (String line : text.split("\n")) {
            if (line.contains("大学") || line.contains("学院") || line.contains("本科") || line.contains("硕士")) {
                return line.trim();
            }
        }
        return null;
    }

    private String findName(String text) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.length() >= 2 && trimmed.length() <= 20
                    && !trimmed.contains("@") && !trimmed.matches(".*\\d{5,}.*")
                    && !trimmed.contains("简历")) {
                return trimmed;
            }
        }
        return "候选人";
    }
}
```

- [ ] **Step 5: 创建 `AiResumeProfileParser`**

Create `backend/src/main/java/com/offerpilot/resume/service/AiResumeProfileParser.java`:
```java
package com.offerpilot.resume.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.resume.domain.ResumeProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class AiResumeProfileParser {

    private static final Logger log = LoggerFactory.getLogger(AiResumeProfileParser.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public AiResumeProfileParser(ChatClient.Builder builder, ObjectMapper objectMapper) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
    }

    public ResumeProfile parse(String rawText) {
        String content = chatClient.prompt()
                .system("""
                        你是简历解析器。只输出 JSON，不要 markdown 代码块。
                        JSON 字段：name, email, phone, yearsOfExperience, skills, projects, education, summary。
                        projects 是数组，每项包含 name 和 description。
                        """)
                .user("简历文本：\n<resume>\n" + rawText + "\n</resume>")
                .call()
                .content();
        try {
            return objectMapper.readValue(cleanJson(content), ResumeProfile.class);
        } catch (Exception ex) {
            log.warn("AI resume parsing failed, falling back to heuristic", ex);
            throw new IllegalStateException("AI resume parsing failed", ex);
        }
    }

    private String cleanJson(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        return trimmed;
    }
}
```

- [ ] **Step 6: 创建 `ResumeProfileService`**

Create `backend/src/main/java/com/offerpilot/resume/service/ResumeProfileService.java`:
```java
package com.offerpilot.resume.service;

import com.offerpilot.resume.domain.ResumeProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ResumeProfileService {

    private static final Logger log = LoggerFactory.getLogger(ResumeProfileService.class);

    private final HeuristicResumeProfileParser heuristicParser;
    private final ObjectProvider<AiResumeProfileParser> aiParserProvider;

    public ResumeProfileService(HeuristicResumeProfileParser heuristicParser,
                                ObjectProvider<AiResumeProfileParser> aiParserProvider) {
        this.heuristicParser = heuristicParser;
        this.aiParserProvider = aiParserProvider;
    }

    public ResumeProfile parse(String rawText) {
        AiResumeProfileParser aiParser = aiParserProvider.getIfAvailable();
        if (aiParser != null) {
            try {
                return aiParser.parse(rawText);
            } catch (Exception ex) {
                log.warn("AI resume parser failed, using heuristic parser");
            }
        }
        return heuristicParser.parse(rawText);
    }
}
```

- [ ] **Step 7: 运行测试，确认通过**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=HeuristicResumeProfileParserTest test
```

Expected: PASS。

- [ ] **Step 8: 提交**

Run:
```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/resume/domain \
  backend/src/main/java/com/offerpilot/resume/service/HeuristicResumeProfileParser.java \
  backend/src/main/java/com/offerpilot/resume/service/AiResumeProfileParser.java \
  backend/src/main/java/com/offerpilot/resume/service/ResumeProfileService.java \
  backend/src/test/java/com/offerpilot/resume/HeuristicResumeProfileParserTest.java
git commit -m "feat(resume): parse structured profile with heuristic and optional AI"
```

---

## Task 6: 简历领域模型、Service 与 API

**Files:**
- Create: `backend/src/main/java/com/offerpilot/resume/domain/Resume.java`
- Create: `backend/src/main/java/com/offerpilot/resume/mapper/ResumeMapper.java`
- Create: `backend/src/main/java/com/offerpilot/resume/service/ResumeService.java`
- Create: `backend/src/main/java/com/offerpilot/resume/web/dto/ResumeView.java`
- Create: `backend/src/main/java/com/offerpilot/resume/web/ResumeController.java`
- Test: `backend/src/test/java/com/offerpilot/resume/ResumeControllerIT.java`

- [ ] **Step 1: 先写失败测试**

Create `backend/src/test/java/com/offerpilot/resume/ResumeControllerIT.java`:
```java
package com.offerpilot.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.user.web.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResumeControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void uploadsAndListsResume() throws Exception {
        String email = "resume-" + UUID.randomUUID() + "@example.com";
        MvcResult login = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, "password123", "Resume Tester"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();

        byte[] docx = TestFiles.docx("""
                张三
                邮箱：zhangsan@example.com
                3 年 Java 后端开发经验
                技能：Java, Spring Boot, MySQL, Redis
                项目：中州养老智能物联网平台
                负责人：设备数据采集与报警规则。
                """);
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);

        mockMvc.perform(multipart("/api/resumes")
                        .file(file)
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.fileName").value("resume.docx"))
                .andExpect(jsonPath("$.data.parseStatus").value("PARSED"))
                .andExpect(jsonPath("$.data.profile.skills").isArray());

        mockMvc.perform(get("/api/resumes")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}
```

Create test helper `backend/src/test/java/com/offerpilot/resume/TestFiles.java`:
```java
package com.offerpilot.resume;

import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayOutputStream;

public final class TestFiles {

    private TestFiles() {
    }

    public static byte[] docx(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String line : text.split("\n")) {
                document.createParagraph().createRun().setText(line);
            }
            document.write(out);
            return out.toByteArray();
        }
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=ResumeControllerIT test
```

Expected: 编译失败或 404，简历 API 不存在。

- [ ] **Step 3: 创建 `Resume` 实体与 Mapper**

Create `backend/src/main/java/com/offerpilot/resume/domain/Resume.java`:
```java
package com.offerpilot.resume.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("resume")
public class Resume {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String fileName;

    private String filePath;

    private String contentType;

    private Long fileSize;

    private String rawText;

    private String parsedJson;

    private String parseStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
```

Create `backend/src/main/java/com/offerpilot/resume/mapper/ResumeMapper.java`:
```java
package com.offerpilot.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerpilot.resume.domain.Resume;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ResumeMapper extends BaseMapper<Resume> {
}
```

- [ ] **Step 4: 创建 `ResumeView`**

Create `backend/src/main/java/com/offerpilot/resume/web/dto/ResumeView.java`:
```java
package com.offerpilot.resume.web.dto;

import com.offerpilot.resume.domain.Resume;
import com.offerpilot.resume.domain.ResumeProfile;

import java.time.LocalDateTime;

public record ResumeView(
        Long id,
        String fileName,
        Long fileSize,
        String parseStatus,
        ResumeProfile profile,
        LocalDateTime createdAt
) {

    public static ResumeView from(Resume resume, ResumeProfile profile) {
        return new ResumeView(
                resume.getId(),
                resume.getFileName(),
                resume.getFileSize(),
                resume.getParseStatus(),
                profile,
                resume.getCreatedAt());
    }
}
```

- [ ] **Step 5: 创建 `ResumeService`**

Create `backend/src/main/java/com/offerpilot/resume/service/ResumeService.java`:
```java
package com.offerpilot.resume.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.resume.domain.Resume;
import com.offerpilot.resume.domain.ResumeProfile;
import com.offerpilot.resume.mapper.ResumeMapper;
import com.offerpilot.resume.storage.FileStorage;
import com.offerpilot.resume.storage.StoredFile;
import com.offerpilot.resume.web.dto.ResumeView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    private final ResumeMapper resumeMapper;
    private final FileStorage fileStorage;
    private final ResumeTextExtractor textExtractor;
    private final ResumeProfileService profileService;
    private final ObjectMapper objectMapper;

    public ResumeService(ResumeMapper resumeMapper,
                         FileStorage fileStorage,
                         ResumeTextExtractor textExtractor,
                         ResumeProfileService profileService,
                         ObjectMapper objectMapper) {
        this.resumeMapper = resumeMapper;
        this.fileStorage = fileStorage;
        this.textExtractor = textExtractor;
        this.profileService = profileService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResumeView upload(Long userId, MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "简历文件读取失败");
        }

        StoredFile stored = fileStorage.store(file, userId);

        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setFileName(stored.originalName());
        resume.setFilePath(stored.relativePath());
        resume.setContentType(stored.contentType());
        resume.setFileSize(stored.size());

        ResumeProfile profile = null;
        try {
            String rawText = textExtractor.extract(content, stored.originalName());
            resume.setRawText(rawText);
            profile = profileService.parse(rawText);
            resume.setParsedJson(objectMapper.writeValueAsString(profile));
            resume.setParseStatus("PARSED");
        } catch (Exception ex) {
            log.warn("Resume parsing failed for {}", stored.originalName(), ex);
            resume.setParseStatus("FAILED");
        }

        resumeMapper.insert(resume);
        return ResumeView.from(resume, profile);
    }

    public List<ResumeView> list(Long userId) {
        return resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getUserId, userId)
                        .orderByDesc(Resume::getCreatedAt))
                .stream()
                .map(this::toView)
                .toList();
    }

    public ResumeView get(Long userId, Long resumeId) {
        return toView(loadOwned(userId, resumeId));
    }

    public void delete(Long userId, Long resumeId) {
        loadOwned(userId, resumeId);
        resumeMapper.deleteById(resumeId);
    }

    public Resume loadOwned(Long userId, Long resumeId) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null || !userId.equals(resume.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "简历不存在");
        }
        return resume;
    }

    private ResumeView toView(Resume resume) {
        ResumeProfile profile = null;
        if (resume.getParsedJson() != null) {
            try {
                profile = objectMapper.readValue(resume.getParsedJson(), ResumeProfile.class);
            } catch (Exception ex) {
                log.warn("Failed to parse stored resume profile {}", resume.getId(), ex);
            }
        }
        return ResumeView.from(resume, profile);
    }
}
```

- [ ] **Step 6: 创建 `ResumeController`**

Create `backend/src/main/java/com/offerpilot/resume/web/ResumeController.java`:
```java
package com.offerpilot.resume.web;

import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.resume.service.ResumeService;
import com.offerpilot.resume.web.dto.ResumeView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping
    public ApiResponse<ResumeView> upload(@AuthenticationPrincipal Long userId,
                                          @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(resumeService.upload(userId, file));
    }

    @GetMapping
    public ApiResponse<List<ResumeView>> list(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(resumeService.list(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ResumeView> get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.ok(resumeService.get(userId, id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        resumeService.delete(userId, id);
        return ApiResponse.ok();
    }
}
```

- [ ] **Step 7: 运行测试，确认通过**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=ResumeControllerIT test
```

Expected: PASS。

- [ ] **Step 8: 提交**

Run:
```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/resume/domain/Resume.java \
  backend/src/main/java/com/offerpilot/resume/mapper/ResumeMapper.java \
  backend/src/main/java/com/offerpilot/resume/service/ResumeService.java \
  backend/src/main/java/com/offerpilot/resume/web \
  backend/src/test/java/com/offerpilot/resume/ResumeControllerIT.java \
  backend/src/test/java/com/offerpilot/resume/TestFiles.java
git commit -m "feat(resume): add upload, list, detail and delete APIs"
```

---

## Task 7: 面试蓝图模型与 Mock 生成器

**Files:**
- Create: `backend/src/main/java/com/offerpilot/interview/domain/InterviewBlueprint.java`
- Create: `backend/src/main/java/com/offerpilot/interview/domain/BlueprintSection.java`
- Create: `backend/src/main/java/com/offerpilot/interview/domain/BlueprintQuestion.java`
- Create: `backend/src/main/java/com/offerpilot/interview/domain/BlueprintRequest.java`
- Create: `backend/src/main/java/com/offerpilot/interview/service/InterviewBlueprintGenerator.java`
- Create: `backend/src/main/java/com/offerpilot/interview/service/MockBlueprintGenerator.java`
- Test: `backend/src/test/java/com/offerpilot/interview/MockBlueprintGeneratorTest.java`

- [ ] **Step 1: 先写失败测试**

Create `backend/src/test/java/com/offerpilot/interview/MockBlueprintGeneratorTest.java`:
```java
package com.offerpilot.interview;

import com.offerpilot.interview.domain.BlueprintRequest;
import com.offerpilot.interview.domain.InterviewBlueprint;
import com.offerpilot.interview.service.MockBlueprintGenerator;
import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.resume.domain.ProjectExperience;
import com.offerpilot.resume.domain.ResumeProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockBlueprintGeneratorTest {

    private final MockBlueprintGenerator generator = new MockBlueprintGenerator();

    @Test
    void generatesEightQuestionsForThirtyMinutes() {
        JobTemplate job = new JobTemplate();
        job.setTitle("Java 后端开发（校招）");
        job.setCategory("Java 后端");
        job.setConfigJson("{\"basic\":30,\"project\":35,\"scenario\":20,\"ai\":15}");

        ResumeProfile profile = new ResumeProfile(
                "张三", "zhangsan@example.com", "13800138000", 3,
                List.of("Java", "Spring Boot", "MySQL", "Redis"),
                List.of(new ProjectExperience("中州养老", "负责设备数据采集与报警规则")),
                "某大学本科", "3 年 Java 后端经验");

        InterviewBlueprint blueprint = generator.generate(new BlueprintRequest(
                job, profile, "STANDARD", 30, "STANDARD"));

        long count = blueprint.sections().stream()
                .mapToLong(section -> section.questions().size())
                .sum();
        assertThat(count).isEqualTo(8);
        assertThat(blueprint.sections()).isNotEmpty();
        assertThat(blueprint.sections().get(0).questions())
                .allSatisfy(question -> {
                    assertThat(question.text()).isNotBlank();
                    assertThat(question.referencePoints()).isNotEmpty();
                });
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=MockBlueprintGeneratorTest test
```

Expected: 编译失败，蓝图类不存在。

- [ ] **Step 3: 创建蓝图模型**

Create `backend/src/main/java/com/offerpilot/interview/domain/BlueprintQuestion.java`:
```java
package com.offerpilot.interview.domain;

import java.util.List;

public record BlueprintQuestion(
        String text,
        int difficulty,
        List<String> knowledgeTags,
        List<String> referencePoints,
        List<String> followUpHints
) {
}
```

Create `backend/src/main/java/com/offerpilot/interview/domain/BlueprintSection.java`:
```java
package com.offerpilot.interview.domain;

import java.util.List;

public record BlueprintSection(String type, int weight, List<BlueprintQuestion> questions) {
}
```

Create `backend/src/main/java/com/offerpilot/interview/domain/InterviewBlueprint.java`:
```java
package com.offerpilot.interview.domain;

import java.util.List;

public record InterviewBlueprint(
        String opening,
        List<BlueprintSection> sections,
        String closing
) {
}
```

Create `backend/src/main/java/com/offerpilot/interview/domain/BlueprintRequest.java`:
```java
package com.offerpilot.interview.domain;

import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.resume.domain.ResumeProfile;

public record BlueprintRequest(
        JobTemplate jobTemplate,
        ResumeProfile resumeProfile,
        String difficulty,
        int durationMinutes,
        String persona
) {
}
```

- [ ] **Step 4: 创建生成器接口**

Create `backend/src/main/java/com/offerpilot/interview/service/InterviewBlueprintGenerator.java`:
```java
package com.offerpilot.interview.service;

import com.offerpilot.interview.domain.BlueprintRequest;
import com.offerpilot.interview.domain.InterviewBlueprint;

public interface InterviewBlueprintGenerator {

    InterviewBlueprint generate(BlueprintRequest request);
}
```

- [ ] **Step 5: 创建 `MockBlueprintGenerator`**

Create `backend/src/main/java/com/offerpilot/interview/service/MockBlueprintGenerator.java`:
```java
package com.offerpilot.interview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.interview.domain.BlueprintQuestion;
import com.offerpilot.interview.domain.BlueprintRequest;
import com.offerpilot.interview.domain.BlueprintSection;
import com.offerpilot.interview.domain.InterviewBlueprint;
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
        boolean microserviceJob = category.contains("微服务");

        Map<String, Integer> weights = weights(request.jobTemplate().getConfigJson(), aiJob);
        int basicCount = total >= 10 ? 4 : 3;
        int projectCount = total <= 6 ? 2 : 3;
        int scenarioCount = total >= 10 ? 2 : 1;
        int aiCount = total - basicCount - projectCount - scenarioCount;

        if (aiJob) {
            basicCount--;
            aiCount++;
        }

        List<BlueprintQuestion> basic = basicQuestions(category);
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
        defaults.put("scenario", aiJob ? 20 : 20);
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

    private List<BlueprintQuestion> take(List<BlueprintQuestion> source, int count) {
        List<BlueprintQuestion> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(source.get(i % source.size()));
        }
        return result;
    }

    private List<BlueprintQuestion> basicQuestions(String category) {
        if (category.contains("AI")) {
            return List.of(
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
            );
        }
        if (category.contains("微服务")) {
            return List.of(
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
            );
        }
        return List.of(
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
        );
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
```

- [ ] **Step 6: 运行测试，确认通过**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=MockBlueprintGeneratorTest test
```

Expected: PASS。

- [ ] **Step 7: 提交**

Run:
```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/interview/domain \
  backend/src/main/java/com/offerpilot/interview/service/InterviewBlueprintGenerator.java \
  backend/src/main/java/com/offerpilot/interview/service/MockBlueprintGenerator.java \
  backend/src/test/java/com/offerpilot/interview/MockBlueprintGeneratorTest.java
git commit -m "feat(interview): add blueprint model and mock generator"
```

---

## Task 8: Spring AI DeepSeek 蓝图生成器（可选启用）

**Files:**
- Create: `backend/src/main/java/com/offerpilot/interview/service/SpringAiBlueprintGenerator.java`

- [ ] **Step 1: 创建 AI 生成器**

Create `backend/src/main/java/com/offerpilot/interview/service/SpringAiBlueprintGenerator.java`:
```java
package com.offerpilot.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.interview.domain.BlueprintRequest;
import com.offerpilot.interview.domain.InterviewBlueprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class SpringAiBlueprintGenerator implements InterviewBlueprintGenerator {

    private static final Logger log = LoggerFactory.getLogger(SpringAiBlueprintGenerator.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final MockBlueprintGenerator fallback;

    public SpringAiBlueprintGenerator(ChatClient.Builder builder,
                                      ObjectMapper objectMapper,
                                      MockBlueprintGenerator fallback) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
        this.fallback = fallback;
    }

    @Override
    public InterviewBlueprint generate(BlueprintRequest request) {
        try {
            int count = switch (request.durationMinutes()) {
                case 20 -> 6;
                case 45 -> 10;
                default -> 8;
            };
            String content = chatClient.prompt()
                    .system("""
                            你是一名资深技术面试官。根据岗位 JD 和候选人简历生成一场模拟面试蓝图。
                            只输出 JSON，不要 markdown 代码块。JSON 结构：
                            {"opening":"...","sections":[{"type":"BASIC","weight":30,"questions":[{"text":"...","difficulty":2,"knowledgeTags":["..."],"referencePoints":["..."],"followUpHints":["..."]}]}],"closing":"..."}
                            type 只能是 BASIC/PROJECT/SCENARIO/AI。所有题目必须有 referencePoints。
                            """)
                    .user("""
                            岗位：%s
                            JD：%s
                            候选人简历：%s
                            难度：%s
                            时长：%d 分钟
                            面试官风格：%s
                            题目总数：%d
                            """.formatted(
                            request.jobTemplate().getTitle(),
                            request.jobTemplate().getJdText(),
                            request.resumeProfile(),
                            request.difficulty(),
                            request.durationMinutes(),
                            request.persona(),
                            count))
                    .call()
                    .content();

            InterviewBlueprint blueprint = objectMapper.readValue(cleanJson(content), InterviewBlueprint.class);
            if (blueprint.sections() == null || blueprint.sections().isEmpty()) {
                throw new IllegalStateException("AI blueprint is empty");
            }
            return blueprint;
        } catch (Exception ex) {
            log.warn("AI blueprint generation failed, falling back to mock generator", ex);
            return fallback.generate(request);
        }
    }

    private String cleanJson(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        return trimmed;
    }
}
```

- [ ] **Step 2: 编译验证**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -DskipTests package
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 3: 用 Mock 模式启动验证**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml spring-boot:run
```

Expected: 应用正常启动，日志中无 Spring AI 相关 bean 创建失败。`APP_AI_ENABLED` 默认为 `false`，所以 `SpringAiBlueprintGenerator` 不会创建。

- [ ] **Step 4: 提交**

Run:
```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/interview/service/SpringAiBlueprintGenerator.java
git commit -m "feat(interview): add optional Spring AI blueprint generator"
```

---

## Task 9: 面试领域模型、Service 与 API

**Files:**
- Create: `backend/src/main/java/com/offerpilot/interview/domain/InterviewSession.java`
- Create: `backend/src/main/java/com/offerpilot/interview/domain/InterviewQuestion.java`
- Create: `backend/src/main/java/com/offerpilot/interview/mapper/InterviewSessionMapper.java`
- Create: `backend/src/main/java/com/offerpilot/interview/mapper/InterviewQuestionMapper.java`
- Create: `backend/src/main/java/com/offerpilot/interview/service/InterviewService.java`
- Create: `backend/src/main/java/com/offerpilot/interview/web/dto/CreateInterviewRequest.java`
- Create: `backend/src/main/java/com/offerpilot/interview/web/dto/QuestionView.java`
- Create: `backend/src/main/java/com/offerpilot/interview/web/dto/InterviewSessionView.java`
- Create: `backend/src/main/java/com/offerpilot/interview/web/InterviewController.java`
- Test: `backend/src/test/java/com/offerpilot/interview/InterviewControllerIT.java`

- [ ] **Step 1: 先写失败测试**

Create `backend/src/test/java/com/offerpilot/interview/InterviewControllerIT.java`:
```java
package com.offerpilot.interview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.resume.TestFiles;
import com.offerpilot.user.web.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterviewControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsAndReadsInterview() throws Exception {
        String email = "interview-" + UUID.randomUUID() + "@example.com";
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, "password123", "Interview Tester"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(registered.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();

        byte[] docx = TestFiles.docx("""
                李四
                邮箱：lisi@example.com
                2 年 Java 开发经验
                技能：Java, Spring Boot, MySQL, Redis
                项目：电商订单系统
                负责订单创建、库存扣减和幂等设计。
                """);
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);
        MvcResult uploaded = mockMvc.perform(multipart("/api/resumes")
                        .file(file)
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long resumeId = objectMapper.readTree(uploaded.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String createBody = objectMapper.writeValueAsString(Map.of(
                "jobTemplateId", 1,
                "resumeId", resumeId,
                "difficulty", "STANDARD",
                "durationMinutes", 30,
                "persona", "STANDARD"));

        MvcResult created = mockMvc.perform(post("/api/interviews")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.questions.length()").value(8))
                .andReturn();

        JsonNode data = objectMapper.readTree(created.getResponse().getContentAsString()).get("data");
        long sessionId = data.get("id").asLong();

        mockMvc.perform(get("/api/interviews/" + sessionId)
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions.length()").value(8));

        mockMvc.perform(get("/api/interviews")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}
```

> `TestFiles` 在 Task 6 中已声明为 `public final class`，`InterviewControllerIT` 可以直接复用。

- [ ] **Step 2: 运行测试，确认失败**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=InterviewControllerIT test
```

Expected: 编译失败或 404。

- [ ] **Step 3: 创建面试实体与 Mapper**

Create `backend/src/main/java/com/offerpilot/interview/domain/InterviewSession.java`:
```java
package com.offerpilot.interview.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("interview_session")
public class InterviewSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long jobTemplateId;

    private Long resumeId;

    private String difficulty;

    private Integer durationMinutes;

    private String persona;

    private String status;

    private String blueprintJson;

    private Integer totalScore;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
```

Create `backend/src/main/java/com/offerpilot/interview/domain/InterviewQuestion.java`:
```java
package com.offerpilot.interview.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("interview_question")
public class InterviewQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Integer seq;

    private String type;

    private Integer difficulty;

    private String content;

    private String knowledgeTags;

    private String referencePoints;

    private String followUpHints;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

Create `backend/src/main/java/com/offerpilot/interview/mapper/InterviewSessionMapper.java`:
```java
package com.offerpilot.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerpilot.interview.domain.InterviewSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InterviewSessionMapper extends BaseMapper<InterviewSession> {
}
```

Create `backend/src/main/java/com/offerpilot/interview/mapper/InterviewQuestionMapper.java`:
```java
package com.offerpilot.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerpilot.interview.domain.InterviewQuestion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InterviewQuestionMapper extends BaseMapper<InterviewQuestion> {
}
```

- [ ] **Step 4: 创建 DTO**

Create `backend/src/main/java/com/offerpilot/interview/web/dto/CreateInterviewRequest.java`:
```java
package com.offerpilot.interview.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateInterviewRequest(
        @NotNull Long jobTemplateId,
        @NotNull Long resumeId,
        @NotBlank String difficulty,
        @NotNull @Min(10) @Max(60) Integer durationMinutes,
        @NotBlank String persona
) {
}
```

Create `backend/src/main/java/com/offerpilot/interview/web/dto/QuestionView.java`:
```java
package com.offerpilot.interview.web.dto;

import java.util.List;

public record QuestionView(
        Long id,
        Integer seq,
        String type,
        Integer difficulty,
        String content,
        List<String> knowledgeTags,
        List<String> referencePoints
) {
}
```

Create `backend/src/main/java/com/offerpilot/interview/web/dto/InterviewSessionView.java`:
```java
package com.offerpilot.interview.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record InterviewSessionView(
        Long id,
        Long jobTemplateId,
        String jobTitle,
        Long resumeId,
        String difficulty,
        Integer durationMinutes,
        String persona,
        String status,
        LocalDateTime createdAt,
        List<QuestionView> questions
) {
}
```

- [ ] **Step 5: 创建 `InterviewService`**

Create `backend/src/main/java/com/offerpilot/interview/service/InterviewService.java`:
```java
package com.offerpilot.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.interview.domain.BlueprintQuestion;
import com.offerpilot.interview.domain.BlueprintRequest;
import com.offerpilot.interview.domain.BlueprintSection;
import com.offerpilot.interview.domain.InterviewBlueprint;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.InterviewSession;
import com.offerpilot.interview.mapper.InterviewQuestionMapper;
import com.offerpilot.interview.mapper.InterviewSessionMapper;
import com.offerpilot.interview.web.dto.CreateInterviewRequest;
import com.offerpilot.interview.web.dto.InterviewSessionView;
import com.offerpilot.interview.web.dto.QuestionView;
import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.job.mapper.JobTemplateMapper;
import com.offerpilot.resume.domain.Resume;
import com.offerpilot.resume.domain.ResumeProfile;
import com.offerpilot.resume.mapper.ResumeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class InterviewService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final JobTemplateMapper jobTemplateMapper;
    private final ResumeMapper resumeMapper;
    private final InterviewBlueprintGenerator blueprintGenerator;
    private final ObjectMapper objectMapper;

    public InterviewService(InterviewSessionMapper sessionMapper,
                            InterviewQuestionMapper questionMapper,
                            JobTemplateMapper jobTemplateMapper,
                            ResumeMapper resumeMapper,
                            InterviewBlueprintGenerator blueprintGenerator,
                            ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.jobTemplateMapper = jobTemplateMapper;
        this.resumeMapper = resumeMapper;
        this.blueprintGenerator = blueprintGenerator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InterviewSessionView create(Long userId, CreateInterviewRequest request) {
        JobTemplate job = jobTemplateMapper.selectById(request.jobTemplateId());
        if (job == null || !"ACTIVE".equals(job.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "岗位模板不存在");
        }

        Resume resume = resumeMapper.selectById(request.resumeId());
        if (resume == null || !userId.equals(resume.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "简历不存在");
        }

        ResumeProfile profile = readProfile(resume);
        InterviewBlueprint blueprint = blueprintGenerator.generate(new BlueprintRequest(
                job, profile, request.difficulty(), request.durationMinutes(), request.persona()));

        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setJobTemplateId(job.getId());
        session.setResumeId(resume.getId());
        session.setDifficulty(request.difficulty());
        session.setDurationMinutes(request.durationMinutes());
        session.setPersona(request.persona());
        session.setStatus("READY");
        session.setBlueprintJson(writeJson(blueprint));
        sessionMapper.insert(session);

        List<QuestionView> questions = saveQuestions(session.getId(), blueprint);
        return toView(session, job.getTitle(), questions);
    }

    public InterviewSessionView get(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        JobTemplate job = jobTemplateMapper.selectById(session.getJobTemplateId());
        List<QuestionView> questions = questionMapper.selectList(
                        new LambdaQueryWrapper<InterviewQuestion>()
                                .eq(InterviewQuestion::getSessionId, sessionId)
                                .orderByAsc(InterviewQuestion::getSeq))
                .stream()
                .map(this::toQuestionView)
                .toList();
        return toView(session, job == null ? "未知岗位" : job.getTitle(), questions);
    }

    public List<InterviewSessionView> list(Long userId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .orderByDesc(InterviewSession::getCreatedAt))
                .stream()
                .map(session -> {
                    JobTemplate job = jobTemplateMapper.selectById(session.getJobTemplateId());
                    return toView(session, job == null ? "未知岗位" : job.getTitle(), List.of());
                })
                .toList();
    }

    private InterviewSession loadOwned(Long userId, Long sessionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "面试不存在");
        }
        return session;
    }

    private List<QuestionView> saveQuestions(Long sessionId, InterviewBlueprint blueprint) {
        List<QuestionView> result = new ArrayList<>();
        int seq = 1;
        for (BlueprintSection section : blueprint.sections()) {
            for (BlueprintQuestion question : section.questions()) {
                InterviewQuestion entity = new InterviewQuestion();
                entity.setSessionId(sessionId);
                entity.setSeq(seq++);
                entity.setType(section.type());
                entity.setDifficulty(question.difficulty());
                entity.setContent(question.text());
                entity.setKnowledgeTags(writeJson(question.knowledgeTags()));
                entity.setReferencePoints(writeJson(question.referencePoints()));
                entity.setFollowUpHints(writeJson(question.followUpHints()));
                entity.setStatus("PENDING");
                questionMapper.insert(entity);
                result.add(toQuestionView(entity));
            }
        }
        return result;
    }

    private QuestionView toQuestionView(InterviewQuestion question) {
        return new QuestionView(
                question.getId(),
                question.getSeq(),
                question.getType(),
                question.getDifficulty(),
                question.getContent(),
                readList(question.getKnowledgeTags()),
                readList(question.getReferencePoints()));
    }

    private InterviewSessionView toView(InterviewSession session, String jobTitle,
                                        List<QuestionView> questions) {
        return new InterviewSessionView(
                session.getId(),
                session.getJobTemplateId(),
                jobTitle,
                session.getResumeId(),
                session.getDifficulty(),
                session.getDurationMinutes(),
                session.getPersona(),
                session.getStatus(),
                session.getCreatedAt(),
                questions);
    }

    private ResumeProfile readProfile(Resume resume) {
        if (resume.getParsedJson() == null) {
            return new ResumeProfile(null, null, null, 0, List.of(), List.of(), null, "");
        }
        try {
            return objectMapper.readValue(resume.getParsedJson(), ResumeProfile.class);
        } catch (Exception ex) {
            return new ResumeProfile(null, null, null, 0, List.of(), List.of(), null, "");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "面试蓝图序列化失败");
        }
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
```

- [ ] **Step 6: 创建 `InterviewController`**

Create `backend/src/main/java/com/offerpilot/interview/web/InterviewController.java`:
```java
package com.offerpilot.interview.web;

import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.interview.service.InterviewService;
import com.offerpilot.interview.web.dto.CreateInterviewRequest;
import com.offerpilot.interview.web.dto.InterviewSessionView;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @PostMapping
    public ApiResponse<InterviewSessionView> create(@AuthenticationPrincipal Long userId,
                                                    @Valid @RequestBody CreateInterviewRequest request) {
        return ApiResponse.ok(interviewService.create(userId, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<InterviewSessionView> get(@AuthenticationPrincipal Long userId,
                                                 @PathVariable Long id) {
        return ApiResponse.ok(interviewService.get(userId, id));
    }

    @GetMapping
    public ApiResponse<List<InterviewSessionView>> list(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(interviewService.list(userId));
    }
}
```

- [ ] **Step 7: 运行测试，确认通过**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=InterviewControllerIT test
```

Expected: PASS。

- [ ] **Step 8: 提交**

Run:
```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/interview/domain/InterviewSession.java \
  backend/src/main/java/com/offerpilot/interview/domain/InterviewQuestion.java \
  backend/src/main/java/com/offerpilot/interview/mapper \
  backend/src/main/java/com/offerpilot/interview/service/InterviewService.java \
  backend/src/main/java/com/offerpilot/interview/web \
  backend/src/test/java/com/offerpilot/interview/InterviewControllerIT.java
git commit -m "feat(interview): add interview creation and detail APIs"
```

---

## Task 10: 前端 API 与面试创建弹窗

**Files:**
- Create: `frontend/src/api/resume.ts`
- Create: `frontend/src/api/interview.ts`
- Create: `frontend/src/components/InterviewSetupDialog.vue`
- Modify: `frontend/src/views/HomeView.vue`

- [ ] **Step 1: 创建简历 API**

Create `frontend/src/api/resume.ts`:
```ts
import http from './http'

export interface ProjectExperience {
  name: string
  description: string
}

export interface ResumeProfile {
  name: string | null
  email: string | null
  phone: string | null
  yearsOfExperience: number
  skills: string[]
  projects: ProjectExperience[]
  education: string | null
  summary: string
}

export interface ResumeView {
  id: number
  fileName: string
  fileSize: number
  parseStatus: string
  profile: ResumeProfile | null
  createdAt: string
}

export const resumeApi = {
  list: () => http.get<never, ResumeView[]>('/resumes'),

  upload: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return http.post<never, ResumeView>('/resumes', form, { timeout: 60000 })
  },

  get: (id: number) => http.get<never, ResumeView>(`/resumes/${id}`),

  remove: (id: number) => http.delete<never, void>(`/resumes/${id}`)
}
```

- [ ] **Step 2: 创建面试 API**

Create `frontend/src/api/interview.ts`:
```ts
import http from './http'

export interface QuestionView {
  id: number
  seq: number
  type: string
  difficulty: number
  content: string
  knowledgeTags: string[]
  referencePoints: string[]
}

export interface InterviewSession {
  id: number
  jobTemplateId: number
  jobTitle: string
  resumeId: number
  difficulty: string
  durationMinutes: number
  persona: string
  status: string
  createdAt: string
  questions: QuestionView[]
}

export interface CreateInterviewRequest {
  jobTemplateId: number
  resumeId: number
  difficulty: string
  durationMinutes: number
  persona: string
}

export const interviewApi = {
  create: (data: CreateInterviewRequest) =>
    http.post<never, InterviewSession>('/interviews', data),

  get: (id: number) => http.get<never, InterviewSession>(`/interviews/${id}`),

  list: () => http.get<never, InterviewSession[]>('/interviews')
}
```

- [ ] **Step 3: 创建面试创建弹窗**

Create `frontend/src/components/InterviewSetupDialog.vue`:
```vue
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import type { JobTemplate } from '@/api/job'
import { resumeApi, type ResumeView } from '@/api/resume'
import { interviewApi, type InterviewSession } from '@/api/interview'

const props = defineProps<{
  modelValue: boolean
  job: JobTemplate | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: [session: InterviewSession]
}>()

const resumes = ref<ResumeView[]>([])
const loading = ref(false)
const uploading = ref(false)
const submitting = ref(false)

const form = reactive({
  resumeId: undefined as number | undefined,
  difficulty: 'STANDARD',
  durationMinutes: 30,
  persona: 'STANDARD'
})

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const selectedResume = computed(() =>
  resumes.value.find((item) => item.id === form.resumeId)
)

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    await loadResumes()
  }
)

async function loadResumes() {
  loading.value = true
  try {
    resumes.value = await resumeApi.list()
    if (!form.resumeId && resumes.value.length > 0) {
      form.resumeId = resumes.value[0].id
    }
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}

async function handleUpload(options: UploadRequestOptions) {
  uploading.value = true
  try {
    const resume = await resumeApi.upload(options.file as File)
    resumes.value.unshift(resume)
    form.resumeId = resume.id
    options.onSuccess(resume)
    ElMessage.success(
      resume.parseStatus === 'PARSED' ? '简历上传并解析成功' : '简历已上传，但解析失败'
    )
  } catch (error) {
    options.onError(error as Error)
  } finally {
    uploading.value = false
  }
}

async function submit() {
  if (!props.job) return
  if (!form.resumeId) {
    ElMessage.warning('请先上传或选择一份简历')
    return
  }

  submitting.value = true
  try {
    const session = await interviewApi.create({
      jobTemplateId: props.job.id,
      resumeId: form.resumeId,
      difficulty: form.difficulty,
      durationMinutes: form.durationMinutes,
      persona: form.persona
    })
    ElMessage.success('面试已创建')
    emit('created', session)
    visible.value = false
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" width="640px" :close-on-click-modal="false">
    <template #header>
      <div class="dialog-header">
        <span>创建模拟面试</span>
        <small v-if="job">{{ job.title }}</small>
      </div>
    </template>

    <div class="setup-body" v-loading="loading">
      <section class="field-block">
        <div class="field-title">
          <span>选择简历</span>
          <el-upload
            :show-file-list="false"
            :http-request="handleUpload"
            accept=".pdf,.docx"
            :disabled="uploading"
          >
            <el-button size="small" :loading="uploading">上传 PDF / DOCX</el-button>
          </el-upload>
        </div>

        <el-empty v-if="!loading && resumes.length === 0" description="还没有简历，先上传一份" :image-size="80" />
        <el-select
          v-else
          v-model="form.resumeId"
          placeholder="选择一份简历"
          style="width: 100%"
        >
          <el-option
            v-for="resume in resumes"
            :key="resume.id"
            :label="`${resume.fileName} · ${resume.parseStatus === 'PARSED' ? '已解析' : '解析失败'}`"
            :value="resume.id"
          />
        </el-select>

        <div v-if="selectedResume?.profile" class="resume-summary">
          <div class="summary-row">
            <strong>{{ selectedResume.profile.name || '候选人' }}</strong>
            <span v-if="selectedResume.profile.yearsOfExperience">
              {{ selectedResume.profile.yearsOfExperience }} 年经验
            </span>
          </div>
          <div class="skill-tags">
            <el-tag
              v-for="skill in (selectedResume.profile.skills ?? []).slice(0, 8)"
              :key="skill"
              size="small"
              effect="plain"
            >
              {{ skill }}
            </el-tag>
          </div>
          <p v-if="selectedResume.profile.projects?.length" class="project-line">
            项目：{{ selectedResume.profile.projects?.map((p) => p.name).join('、') }}
          </p>
        </div>
      </section>

      <section class="field-block">
        <div class="field-title"><span>难度</span></div>
        <el-radio-group v-model="form.difficulty">
          <el-radio-button value="EASY">简单</el-radio-button>
          <el-radio-button value="STANDARD">标准</el-radio-button>
          <el-radio-button value="HARD">挑战</el-radio-button>
        </el-radio-group>
      </section>

      <section class="field-block">
        <div class="field-title"><span>时长</span></div>
        <el-radio-group v-model="form.durationMinutes">
          <el-radio-button :value="20">20 分钟</el-radio-button>
          <el-radio-button :value="30">30 分钟</el-radio-button>
          <el-radio-button :value="45">45 分钟</el-radio-button>
        </el-radio-group>
      </section>

      <section class="field-block">
        <div class="field-title"><span>面试官风格</span></div>
        <el-radio-group v-model="form.persona">
          <el-radio-button value="GENTLE">温和</el-radio-button>
          <el-radio-button value="STANDARD">标准</el-radio-button>
          <el-radio-button value="PRESSURE">压力面</el-radio-button>
        </el-radio-group>
      </section>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        生成面试蓝图
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #17375e;
  font-size: 20px;
  font-weight: 700;
}

.dialog-header small {
  color: #7f8c9d;
  font-size: 13px;
  font-weight: 400;
}

.setup-body {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.field-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #17375e;
  font-size: 14px;
  font-weight: 700;
}

.resume-summary {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #f5f8fc;
  border: 1px solid #e4ecf5;
}

.summary-row {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #17375e;
}

.summary-row span {
  color: #7f8c9d;
  font-size: 12px;
}

.skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.project-line {
  margin: 10px 0 0;
  color: #6b7a8c;
  font-size: 13px;
  line-height: 1.6;
}
</style>
```

- [ ] **Step 4: 修改 `HomeView.vue` 接入弹窗**

在 `<script setup>` 中导入：
```ts
import InterviewSetupDialog from '@/components/InterviewSetupDialog.vue'
import type { InterviewSession } from '@/api/interview'
```

将 `startInterview` 改为：
```ts
const setupVisible = ref(false)
const selectedJob = ref<JobTemplate | null>(null)

function startInterview(job: JobTemplate) {
  selectedJob.value = job
  setupVisible.value = true
}

function handleInterviewCreated(session: InterviewSession) {
  setupVisible.value = false
  router.push(`/interviews/${session.id}`)
}
```

在 `</main>` 前（或根节点内）加入：
```vue
    <InterviewSetupDialog
      v-model="setupVisible"
      :job="selectedJob"
      @created="handleInterviewCreated"
    />
```

- [ ] **Step 5: 安装/构建验证**

Run:
```bash
cd /e/work/frontend
npm run build
```

Expected: 构建成功。

- [ ] **Step 6: 提交**

Run:
```bash
cd /e/work
git add frontend/src/api/resume.ts frontend/src/api/interview.ts \
  frontend/src/components/InterviewSetupDialog.vue frontend/src/views/HomeView.vue
git commit -m "feat(frontend): add interview setup dialog with resume upload"
```

---

## Task 11: 前端面试预览页

**Files:**
- Create: `frontend/src/views/InterviewSessionView.vue`
- Modify: `frontend/src/router/index.ts`

- [ ] **Step 1: 创建面试预览页**

Create `frontend/src/views/InterviewSessionView.vue`:
```vue
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { interviewApi, type InterviewSession, type QuestionView } from '@/api/interview'

const route = useRoute()
const router = useRouter()

const session = ref<InterviewSession | null>(null)
const loading = ref(true)

const typeLabels: Record<string, string> = {
  BASIC: '基础',
  PROJECT: '项目',
  SCENARIO: '场景',
  AI: 'AI 工程'
}

const difficultyLabels: Record<number, string> = {
  1: '简单',
  2: '中等',
  3: '困难'
}

const groupedQuestions = computed(() => {
  const map = new Map<string, QuestionView[]>()
  for (const question of session.value?.questions ?? []) {
    const list = map.get(question.type) ?? []
    list.push(question)
    map.set(question.type, list)
  }
  return Array.from(map.entries()).map(([type, questions]) => ({
    type,
    label: typeLabels[type] ?? type,
    questions
  }))
})

onMounted(async () => {
  try {
    session.value = await interviewApi.get(Number(route.params.id))
  } catch {
    router.replace('/')
  } finally {
    loading.value = false
  }
})

function backHome() {
  router.push('/')
}
</script>

<template>
  <div class="session-page">
    <header class="topbar">
      <button class="back-link" type="button" @click="backHome">← 返回岗位</button>
      <div class="brand">OfferPilot</div>
      <span class="status">蓝图预览</span>
    </header>

    <main v-loading="loading" class="content">
      <template v-if="session">
        <section class="hero">
          <div>
            <p class="eyebrow">INTERVIEW BLUEPRINT</p>
            <h1>{{ session.jobTitle }}</h1>
            <div class="meta">
              <span>{{ session.difficulty === 'HARD' ? '挑战' : session.difficulty === 'EASY' ? '简单' : '标准' }}</span>
              <span>{{ session.durationMinutes }} 分钟</span>
              <span>{{ session.questions.length }} 道题</span>
              <span>{{ session.persona === 'PRESSURE' ? '压力面' : session.persona === 'GENTLE' ? '温和' : '标准' }}</span>
            </div>
          </div>
          <el-button type="primary" size="large" disabled>开始答题（W3 开放）</el-button>
        </section>

        <section v-for="group in groupedQuestions" :key="group.type" class="question-group">
          <div class="group-header">
            <h2>{{ group.label }}</h2>
            <span>{{ group.questions.length }} 题</span>
          </div>
          <article
            v-for="question in group.questions"
            :key="question.id"
            class="question-card"
          >
            <div class="question-top">
              <span class="seq">Q{{ question.seq }}</span>
              <el-tag size="small" effect="plain">{{ difficultyLabels[question.difficulty] || '中等' }}</el-tag>
              <el-tag
                v-for="tag in question.knowledgeTags"
                :key="tag"
                size="small"
                type="info"
                effect="plain"
              >
                {{ tag }}
              </el-tag>
            </div>
            <p class="question-text">{{ question.content }}</p>
          </article>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped>
.session-page {
  min-height: 100vh;
}

.topbar {
  height: 68px;
  padding: 0 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.86);
  border-bottom: 1px solid #e4ecf5;
  backdrop-filter: blur(12px);
}

.brand {
  color: #17375e;
  font-size: 20px;
  font-weight: 800;
}

.back-link {
  border: 0;
  background: transparent;
  color: #2e75b6;
  cursor: pointer;
  font-size: 14px;
  padding: 0;
}

.status {
  color: #7f8c9d;
  font-size: 13px;
}

.content {
  max-width: 1100px;
  margin: 0 auto;
  padding: 40px 28px 72px;
}

.hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 28px;
  border-bottom: 1px solid #e4ecf5;
}

.eyebrow {
  margin: 0 0 10px;
  color: #2e75b6;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.hero h1 {
  margin: 0 0 14px;
  color: #17375e;
  font-size: clamp(28px, 3vw, 40px);
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.meta span {
  padding: 4px 10px;
  border-radius: 999px;
  background: #eef4fb;
  color: #2e75b6;
  font-size: 12px;
}

.question-group {
  margin-top: 34px;
}

.group-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}

.group-header h2 {
  margin: 0;
  color: #17375e;
  font-size: 20px;
}

.group-header span {
  color: #8a97a8;
  font-size: 13px;
}

.question-card {
  padding: 20px 22px;
  margin-bottom: 12px;
  border: 1px solid #e4ecf5;
  border-radius: 14px;
  background: #fff;
}

.question-top {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.seq {
  color: #2e75b6;
  font-size: 13px;
  font-weight: 700;
}

.question-text {
  margin: 0;
  color: #33475b;
  font-size: 15px;
  line-height: 1.8;
}

@media (max-width: 760px) {
  .topbar {
    padding: 0 20px;
  }

  .content {
    padding: 28px 18px 56px;
  }

  .hero {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
```

- [ ] **Step 2: 修改路由**

在 `frontend/src/router/index.ts` 的 routes 中加入：
```ts
    {
      path: '/interviews/:id',
      name: 'interview-session',
      component: () => import('@/views/InterviewSessionView.vue'),
      meta: { requiresAuth: true }
    },
```

- [ ] **Step 3: 构建验证**

Run:
```bash
cd /e/work/frontend
npm run build
```

Expected: 构建成功。

- [ ] **Step 4: 提交**

Run:
```bash
cd /e/work
git add frontend/src/views/InterviewSessionView.vue frontend/src/router/index.ts
git commit -m "feat(frontend): add interview blueprint preview page"
```

---

## Task 12: 端到端验收与文档

**Files:**
- Modify: `README.md`

- [ ] **Step 1: 运行后端全量测试**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml test
```

Expected: 所有测试 PASS（W1 + W2）。

- [ ] **Step 2: 构建前端**

Run:
```bash
cd /e/work/frontend
npm run build
```

Expected: `✓ built in ...`。

- [ ] **Step 3: 启动后端与前端**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml spring-boot:run
```
另一个终端：
```bash
cd /e/work/frontend
npm run dev
```

- [ ] **Step 4: 准备测试简历**

Run:
```bash
PYTHONUTF8=1 python - <<'PY'
from docx import Document
doc = Document()
doc.add_paragraph("张三")
doc.add_paragraph("邮箱：zhangsan@example.com 手机：13800138000")
doc.add_paragraph("3 年 Java 后端开发经验")
doc.add_paragraph("技能：Java, Spring Boot, MySQL, Redis, MyBatis")
doc.add_paragraph("项目：中州养老智能物联网平台")
doc.add_paragraph("负责设备数据采集、报警规则与 AI 健康评估集成。")
doc.save("C:/Users/Administrator/AppData/Local/Temp/offerpilot_sample_resume.docx")
print("saved")
PY
```

- [ ] **Step 5: 浏览器端验收**

1. 打开 http://localhost:5173/login，注册新账号。
2. 首页点击任意岗位的“开始面试”。
3. 弹窗中上传 `offerpilot_sample_resume.docx`。
4. 确认简历摘要显示姓名、年限、技能标签。
5. 选择难度“标准”、时长“30 分钟”、风格“标准”，点击“生成面试蓝图”。
6. 自动跳转 `/interviews/{id}`，应看到 8 道题，按基础/项目/场景/AI 分组。
7. 返回首页，再次打开弹窗，确认简历列表中存在刚上传的简历。

- [ ] **Step 6: 验证无 AI Key 时 Mock 模式可用**

确认 `APP_AI_ENABLED` 未设置为 true（默认 false），上述流程应正常完成。若设置了 `DEEPSEEK_API_KEY`，不要在验收中启用真实 AI，避免产生费用。

- [ ] **Step 7: 更新 README**

将“当前进度”改为：
```markdown
当前进度：P0 Week 2 已完成（简历上传解析、创建面试、面试蓝图、前端创建弹窗与预览页）。

下一步：P0 Week 3（多轮对话、SSE、追问/提示/跳过、状态机）。
```

在 API 表格中追加：
```markdown
| POST | `/api/resumes` | 上传 PDF/DOCX 简历并解析 |
| GET | `/api/resumes` | 当前用户简历列表 |
| GET | `/api/resumes/{id}` | 简历详情 |
| DELETE | `/api/resumes/{id}` | 删除简历 |
| POST | `/api/interviews` | 创建面试并生成蓝图 |
| GET | `/api/interviews/{id}` | 面试详情与题目 |
| GET | `/api/interviews` | 面试历史 |
```

- [ ] **Step 8: 提交**

Run:
```bash
cd /e/work
git add README.md
git commit -m "docs: mark P0 week 2 complete"
git status --short
```

Expected: 工作区干净。

---

## 自检结果

- **Spec coverage:** W2 的简历上传/解析、创建面试、面试蓝图、前端弹窗/预览页均已覆盖；多轮对话、评分、RAG 明确留到 W3-W5。
- **Placeholder scan:** 没有 TBD/TODO；所有代码步骤都有完整代码；`TestFiles` 的可见性问题已在 Task 9 中明确要求改为 public。
- **Type consistency:** `ResumeProfile`、`ResumeView`、`BlueprintRequest`、`InterviewBlueprint`、`InterviewSessionView`、`QuestionView` 的字段在前后端保持一致；`MockBlueprintGenerator` 与 `SpringAiBlueprintGenerator` 都实现 `InterviewBlueprintGenerator`，按 `app.ai.enabled` 条件启用。
- **默认可用性:** `app.ai.enabled=false` 时全部功能可用，不依赖 DeepSeek Key；开启 AI 后失败自动回退 Mock。

---

## 执行记录（2026-10-27）

- 分支：`feat/p0-week2`（基于 `feat/p0-week1`）。
- 后端测试：`mvn -f backend/pom.xml test` → **21 tests, 0 failures, 0 errors**（含 AI 模式 Bean 装配测试）。
- 前端构建：`npm --prefix frontend run build` → 成功。
- 浏览器验收（Playwright + Chrome）：
  - 注册 → 首页 → 打开创建面试弹窗 → 上传 DOCX 简历 → 解析出「张三 / 3 年经验 / Java、Spring Boot…」→ 选择 30 分钟标准面 → 生成蓝图 → 跳转 `/interviews/1`。
  - 面试预览页显示 8 道题，分组为「基础 3 / 项目 3 / 场景 1 / AI 工程 1」。
- 实施中的调整：
  - `MockBlueprintGenerator` 改为常驻 Bean，`SpringAiBlueprintGenerator` 加 `@Primary` + 条件启用，保证 AI 模式可注入 Mock 作为回退。
  - 面试题数量分配修正为 6/8/10 题三种时长严格匹配总数。
  - 新增 `AiWiringTest` 验证 `app.ai.enabled=true` 时优先使用 `SpringAiBlueprintGenerator`。
  - 前端上传错误回调使用 `options.onError(error as never)` 适配 Element Plus 的 `UploadAjaxError` 类型。
- 尚未推送：GitHub 当前网络不可达；`feat/p0-week2` 的所有提交仍在本地，待网络恢复后推送。
