# OfferPilot W5B：运营后台题库 CRUD 与每日额度实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 增加管理员题库 CRUD 后台和每日面试额度：普通用户每天 3 场，管理员不限；前端提供题库管理页和创建面试弹窗中的剩余额度提示。

**Architecture:** 新增 `user_quota` 表，`QuotaService` 在创建面试时扣减额度，`GET /api/quota/me` 查询剩余额度。管理员通过 `sys_user.role = ADMIN` 识别，使用 V9 迁移种子管理员账号；`/api/admin/questions` 提供分页 CRUD，前端 `/admin/questions` 页面调用。额度限制用 MySQL/H2 持久化，暂不引入 Redis；管理员账号种子密码为 `admin123`。

**Tech Stack:** Java 21、Spring Boot 3.5.16、Spring Security、MyBatis-Plus、MySQL/H2、Vue3 + Element Plus。

---

## 0. 范围与完成定义

- 管理员账号 `admin@offerpilot.local / admin123` 可登录。
- 管理员可对题库进行分页查询、新增、编辑、删除。
- 普通用户访问管理员接口返回 403。
- 普通用户每日创建面试上限 3 场，第 4 场返回 429/额度不足；管理员不限。
- 前端新增 `/admin/questions` 题库管理页；创建面试弹窗显示“今日剩余 X 场”。
- 后端测试、前端构建、浏览器验收通过。

**不做：** 支付/会员、Redis 分布式限流、多管理员权限细分。

---

## Task 1: V9 管理员账号与额度服务

**Files:**
- Create: `backend/src/main/resources/db/migration/V9__admin_and_quota.sql`
- Create: `backend/src/main/java/com/offerpilot/quota/domain/UserQuota.java`
- Create: `backend/src/main/java/com/offerpilot/quota/mapper/UserQuotaMapper.java`
- Create: `backend/src/main/java/com/offerpilot/quota/web/dto/QuotaView.java`
- Create: `backend/src/main/java/com/offerpilot/quota/service/QuotaService.java`
- Create: `backend/src/main/java/com/offerpilot/quota/web/QuotaController.java`
- Modify: `backend/src/main/java/com/offerpilot/common/exception/ErrorCode.java`
- Modify: `backend/src/main/java/com/offerpilot/common/exception/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/service/InterviewService.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/offerpilot/quota/SchemaV9IT.java`
- Test: `backend/src/test/java/com/offerpilot/quota/QuotaServiceTest.java`

- [x] **Step 1: 创建分支**

```bash
cd /e/work
git checkout -b feat/p0-week5b-admin-quota
```

- [x] **Step 2: 先写 V9 测试**

Create `backend/src/test/java/com/offerpilot/quota/SchemaV9IT.java`:
```java
package com.offerpilot.quota;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaV9IT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsQuotaTableAndAdminUser() {
        Integer quotaTable = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = 'user_quota'",
                Integer.class);
        Integer admin = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE email = 'admin@offerpilot.local' AND role = 'ADMIN'",
                Integer.class);
        assertThat(quotaTable).isEqualTo(1);
        assertThat(admin).isEqualTo(1);
    }
}
```

- [x] **Step 3: 运行测试，确认失败**

```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=SchemaV9IT test
```

Expected: FAIL。

- [x] **Step 4: 创建 V9 迁移**

Create `backend/src/main/resources/db/migration/V9__admin_and_quota.sql`:
```sql
CREATE TABLE user_quota (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    plan VARCHAR(20) NOT NULL DEFAULT 'FREE',
    period_start DATETIME(3) NOT NULL,
    period_end DATETIME(3) NOT NULL,
    session_limit INT NOT NULL DEFAULT 3,
    session_used INT NOT NULL DEFAULT 0,
    token_limit INT NOT NULL DEFAULT 200000,
    token_used INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE UNIQUE INDEX uk_user_quota_user ON user_quota(user_id);

INSERT INTO sys_user (email, password_hash, nickname, role, status, deleted, created_at, updated_at)
SELECT 'admin@offerpilot.local',
       '$2a$10$nNb26GYj3EUuomya0HhIR.QqXQOQ.qUTme3MZbYrNPLK90J/y2emC',
       'Admin', 'ADMIN', 'ACTIVE', 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE email = 'admin@offerpilot.local');
```

- [x] **Step 5: 增加额度错误码**

在 `ErrorCode` 中加入：
```java
    QUOTA_EXCEEDED(42902, "额度不足"),
```
在 `GlobalExceptionHandler` 的 switch 中加入：
```java
            case QUOTA_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
```

- [x] **Step 6: 创建额度实体与 DTO**

Create `backend/src/main/java/com/offerpilot/quota/domain/UserQuota.java`:
```java
package com.offerpilot.quota.domain;

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
@TableName("user_quota")
public class UserQuota {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String plan;

    private LocalDateTime periodStart;

    private LocalDateTime periodEnd;

    private Integer sessionLimit;

    private Integer sessionUsed;

    private Integer tokenLimit;

    private Integer tokenUsed;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

Create `backend/src/main/java/com/offerpilot/quota/mapper/UserQuotaMapper.java`:
```java
package com.offerpilot.quota.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerpilot.quota.domain.UserQuota;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserQuotaMapper extends BaseMapper<UserQuota> {
}
```

Create `backend/src/main/java/com/offerpilot/quota/web/dto/QuotaView.java`:
```java
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
```

- [x] **Step 7: 创建 `QuotaService`**

Create `backend/src/main/java/com/offerpilot/quota/service/QuotaService.java`:
```java
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

    public QuotaService(UserQuotaMapper quotaMapper,
                        @Value("${app.quota.free-daily-sessions:3}") int freeDailySessions,
                        @Value("${app.quota.free-daily-tokens:200000}") int freeDailyTokens) {
        this.quotaMapper = quotaMapper;
        this.freeDailySessions = freeDailySessions;
        this.freeDailyTokens = freeDailyTokens;
    }

    @Transactional
    public void checkAndConsumeSession(Long userId) {
        if (isAdmin()) {
            return;
        }
        UserQuota quota = getOrCreate(userId);
        resetIfNeeded(quota);
        if (quota.getSessionUsed() >= quota.getSessionLimit()) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "今日面试额度已用完");
        }
        quota.setSessionUsed(quota.getSessionUsed() + 1);
        quotaMapper.updateById(quota);
    }

    public QuotaView me(Long userId) {
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
```

- [x] **Step 8: 创建额度接口**

Create `backend/src/main/java/com/offerpilot/quota/web/QuotaController.java`:
```java
package com.offerpilot.quota.web;

import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.quota.service.QuotaService;
import com.offerpilot.quota.web.dto.QuotaView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quota")
public class QuotaController {

    private final QuotaService quotaService;

    public QuotaController(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @GetMapping("/me")
    public ApiResponse<QuotaView> me(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(quotaService.me(userId));
    }
}
```

- [x] **Step 9: 在创建面试时扣减额度**

在 `InterviewService` 构造函数中增加 `QuotaService quotaService` 字段与参数，并在 `create` 生成蓝图后、插入 session 前调用：
```java
        quotaService.checkAndConsumeSession(userId);
```
导入：
```java
import com.offerpilot.quota.service.QuotaService;
```

- [x] **Step 10: 配置额度**

在 `application.yml` 的 `app:` 下加入：
```yaml
  quota:
    free-daily-sessions: 3
    free-daily-tokens: 200000
```

- [x] **Step 11: 写额度服务测试**

Create `backend/src/test/java/com/offerpilot/quota/QuotaServiceTest.java`:
```java
package com.offerpilot.quota;

import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.quota.service.QuotaService;
import com.offerpilot.user.domain.User;
import com.offerpilot.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QuotaServiceTest {

    @Autowired
    private QuotaService quotaService;

    @Autowired
    private UserMapper userMapper;

    @Test
    void limitsFreeUserToThreeSessions() {
        SecurityContextHolder.clearContext();
        User user = new User();
        user.setEmail("quota-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$placeholder");
        user.setNickname("Quota Tester");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        userMapper.insert(user);

        quotaService.checkAndConsumeSession(user.getId());
        quotaService.checkAndConsumeSession(user.getId());
        quotaService.checkAndConsumeSession(user.getId());

        assertThatThrownBy(() -> quotaService.checkAndConsumeSession(user.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("额度");
        assertThat(quotaService.me(user.getId()).remainingSessions()).isZero();
    }
}
```

- [x] **Step 12: 运行测试**

```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=SchemaV9IT,QuotaServiceTest test
```

Expected: PASS。若 H2 不支持 `INSERT ... SELECT ... WHERE NOT EXISTS`，将管理员种子改为 `MERGE INTO sys_user KEY(email) VALUES (...)`。

- [x] **Step 13: 提交**

```bash
cd /e/work
git add backend/src/main/resources/db/migration/V9__admin_and_quota.sql \
  backend/src/main/java/com/offerpilot/quota \
  backend/src/main/java/com/offerpilot/common/exception/ErrorCode.java \
  backend/src/main/java/com/offerpilot/common/exception/GlobalExceptionHandler.java \
  backend/src/main/java/com/offerpilot/interview/service/InterviewService.java \
  backend/src/main/resources/application.yml \
  backend/src/test/java/com/offerpilot/quota
git commit -m "feat(quota): add admin account and daily interview quota"
```

---

## Task 2: 管理员题库 CRUD API

**Files:**
- Create: `backend/src/main/java/com/offerpilot/questionbank/web/dto/QuestionBankAdminRequest.java`
- Create: `backend/src/main/java/com/offerpilot/questionbank/web/dto/QuestionBankAdminView.java`
- Create: `backend/src/main/java/com/offerpilot/questionbank/web/dto/PageResult.java`
- Create: `backend/src/main/java/com/offerpilot/questionbank/service/QuestionBankAdminService.java`
- Create: `backend/src/main/java/com/offerpilot/questionbank/web/AdminQuestionController.java`
- Test: `backend/src/test/java/com/offerpilot/questionbank/AdminQuestionControllerIT.java`

- [x] **Step 1: 先写管理员接口测试**

Create `backend/src/test/java/com/offerpilot/questionbank/AdminQuestionControllerIT.java`:
```java
package com.offerpilot.questionbank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.interview.web.dto.LoginRequest;
import com.offerpilot.user.web.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminQuestionControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanCrudQuestionsAndUserCannot() throws Exception {
        String adminToken = adminToken();

        MvcResult created = mockMvc.perform(post("/api/admin/questions")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "category", "Java 基础",
                                "level", "校招",
                                "tags", "Java,测试",
                                "difficulty", 2,
                                "question", "测试题目：HashMap 的负载因子是多少？",
                                "referenceAnswer", "默认 0.75。",
                                "source", "测试",
                                "status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(get("/api/admin/questions")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .param("keyword", "负载因子"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(put("/api/admin/questions/" + id)
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "category", "Java 基础",
                                "level", "校招",
                                "tags", "Java,测试",
                                "difficulty", 2,
                                "question", "更新后的题目：HashMap 负载因子？",
                                "referenceAnswer", "默认 0.75。",
                                "source", "测试",
                                "status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question").value("更新后的题目：HashMap 负载因子？"));

        mockMvc.perform(delete("/api/admin/questions/" + id)
                        .header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        String userToken = userToken();
        mockMvc.perform(get("/api/admin/questions")
                        .header(AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    private String adminToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("admin@offerpilot.local", "admin123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private String userToken() throws Exception {
        String email = "admin-test-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, "password123", "Normal User"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }
}
```

- [x] **Step 2: 运行测试，确认失败**

```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=AdminQuestionControllerIT test
```

Expected: FAIL，管理员接口不存在。

- [x] **Step 3: 创建 DTO**

Create `backend/src/main/java/com/offerpilot/questionbank/web/dto/QuestionBankAdminRequest.java`:
```java
package com.offerpilot.questionbank.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QuestionBankAdminRequest(
        @NotBlank String category,
        @NotBlank String level,
        @NotBlank String tags,
        @NotNull @Min(1) @Max(3) Integer difficulty,
        @NotBlank String question,
        @NotBlank String referenceAnswer,
        String source,
        String status
) {
}
```

Create `backend/src/main/java/com/offerpilot/questionbank/web/dto/QuestionBankAdminView.java`:
```java
package com.offerpilot.questionbank.web.dto;

import com.offerpilot.questionbank.domain.QuestionBank;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public record QuestionBankAdminView(
        Long id,
        String category,
        String level,
        List<String> tags,
        Integer difficulty,
        String question,
        String referenceAnswer,
        String source,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static QuestionBankAdminView from(QuestionBank bank) {
        List<String> tags = bank.getTags() == null ? List.of()
                : Arrays.stream(bank.getTags().split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        return new QuestionBankAdminView(
                bank.getId(), bank.getCategory(), bank.getLevel(), tags,
                bank.getDifficulty(), bank.getQuestion(), bank.getReferenceAnswer(),
                bank.getSource(), bank.getStatus(), bank.getCreatedAt(), bank.getUpdatedAt());
    }
}
```

Create `backend/src/main/java/com/offerpilot/questionbank/web/dto/PageResult.java`:
```java
package com.offerpilot.questionbank.web.dto;

import java.util.List;

public record PageResult<T>(List<T> records, long total, long page, long size) {
}
```

- [x] **Step 4: 创建管理员题库服务**

Create `backend/src/main/java/com/offerpilot/questionbank/service/QuestionBankAdminService.java`:
```java
package com.offerpilot.questionbank.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.questionbank.domain.QuestionBank;
import com.offerpilot.questionbank.mapper.QuestionBankMapper;
import com.offerpilot.questionbank.web.dto.PageResult;
import com.offerpilot.questionbank.web.dto.QuestionBankAdminRequest;
import com.offerpilot.questionbank.web.dto.QuestionBankAdminView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class QuestionBankAdminService {

    private final QuestionBankMapper questionBankMapper;

    public QuestionBankAdminService(QuestionBankMapper questionBankMapper) {
        this.questionBankMapper = questionBankMapper;
    }

    public PageResult<QuestionBankAdminView> page(long page, long size, String keyword) {
        Page<QuestionBank> request = new Page<>(Math.max(1, page), Math.min(50, Math.max(1, size)));
        LambdaQueryWrapper<QuestionBank> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(query -> query
                    .like(QuestionBank::getQuestion, keyword)
                    .or()
                    .like(QuestionBank::getCategory, keyword)
                    .or()
                    .like(QuestionBank::getTags, keyword));
        }
        wrapper.orderByDesc(QuestionBank::getId);
        Page<QuestionBank> result = questionBankMapper.selectPage(request, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(QuestionBankAdminView::from).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    public QuestionBankAdminView get(Long id) {
        return QuestionBankAdminView.from(load(id));
    }

    @Transactional
    public QuestionBankAdminView create(QuestionBankAdminRequest request) {
        QuestionBank bank = new QuestionBank();
        apply(bank, request);
        questionBankMapper.insert(bank);
        return QuestionBankAdminView.from(bank);
    }

    @Transactional
    public QuestionBankAdminView update(Long id, QuestionBankAdminRequest request) {
        QuestionBank bank = load(id);
        apply(bank, request);
        questionBankMapper.updateById(bank);
        return QuestionBankAdminView.from(bank);
    }

    @Transactional
    public void delete(Long id) {
        load(id);
        questionBankMapper.deleteById(id);
    }

    private QuestionBank load(Long id) {
        QuestionBank bank = questionBankMapper.selectById(id);
        if (bank == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        return bank;
    }

    private void apply(QuestionBank bank, QuestionBankAdminRequest request) {
        bank.setCategory(request.category().trim());
        bank.setLevel(request.level().trim());
        bank.setTags(normalizeTags(request.tags()));
        bank.setDifficulty(request.difficulty());
        bank.setQuestion(request.question().trim());
        bank.setReferenceAnswer(request.referenceAnswer().trim());
        bank.setSource(request.source());
        String status = request.status() == null ? "ACTIVE" : request.status().toUpperCase(Locale.ROOT);
        bank.setStatus("INACTIVE".equals(status) ? "INACTIVE" : "ACTIVE");
    }

    private String normalizeTags(String tags) {
        List<String> values = Arrays.stream(tags.split("[,，、]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        return String.join(",", values);
    }
}
```

- [x] **Step 5: 创建管理员控制器**

Create `backend/src/main/java/com/offerpilot/questionbank/web/AdminQuestionController.java`:
```java
package com.offerpilot.questionbank.web;

import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.questionbank.service.QuestionBankAdminService;
import com.offerpilot.questionbank.web.dto.PageResult;
import com.offerpilot.questionbank.web.dto.QuestionBankAdminRequest;
import com.offerpilot.questionbank.web.dto.QuestionBankAdminView;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/questions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminQuestionController {

    private final QuestionBankAdminService adminService;

    public AdminQuestionController(QuestionBankAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public ApiResponse<PageResult<QuestionBankAdminView>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(adminService.page(page, size, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionBankAdminView> get(@PathVariable Long id) {
        return ApiResponse.ok(adminService.get(id));
    }

    @PostMapping
    public ApiResponse<QuestionBankAdminView> create(@Valid @RequestBody QuestionBankAdminRequest request) {
        return ApiResponse.ok(adminService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<QuestionBankAdminView> update(@PathVariable Long id,
                                                     @Valid @RequestBody QuestionBankAdminRequest request) {
        return ApiResponse.ok(adminService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminService.delete(id);
        return ApiResponse.ok();
    }
}
```

- [x] **Step 6: 运行测试**

```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=AdminQuestionControllerIT test
```

Expected: PASS。

- [x] **Step 7: 全量测试**

```bash
cd /e/work
mvn -f backend/pom.xml test
```

Expected: 所有测试 PASS。

- [x] **Step 8: 提交**

```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/questionbank/web/dto/QuestionBankAdminRequest.java \
  backend/src/main/java/com/offerpilot/questionbank/web/dto/QuestionBankAdminView.java \
  backend/src/main/java/com/offerpilot/questionbank/web/dto/PageResult.java \
  backend/src/main/java/com/offerpilot/questionbank/service/QuestionBankAdminService.java \
  backend/src/main/java/com/offerpilot/questionbank/web/AdminQuestionController.java \
  backend/src/test/java/com/offerpilot/questionbank/AdminQuestionControllerIT.java
git commit -m "feat(admin): add question bank CRUD APIs"
```

---

## Task 3: 前端管理员题库页与额度提示

**Files:**
- Modify: `frontend/src/api/interview.ts`
- Create: `frontend/src/views/AdminQuestionsView.vue`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/views/HomeView.vue`
- Modify: `frontend/src/components/InterviewSetupDialog.vue`
- Modify: `README.md`

- [x] **Step 1: 增加前端类型与 API**

在 `frontend/src/api/interview.ts` 中增加：
```ts
export interface QuotaView {
  plan: string
  sessionLimit: number
  sessionUsed: number
  remainingSessions: number
  tokenLimit: number
  tokenUsed: number
  resetAt: string | null
}

export interface AdminQuestion {
  id: number
  category: string
  level: string
  tags: string[]
  difficulty: number
  question: string
  referenceAnswer: string
  source: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface AdminQuestionRequest {
  category: string
  level: string
  tags: string
  difficulty: number
  question: string
  referenceAnswer: string
  source: string
  status: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}
```

在文件末尾增加：
```ts
export const quotaApi = {
  me: () => http.get<never, QuotaView>('/quota/me')
}

export const adminQuestionApi = {
  page: (params: { page: number; size: number; keyword?: string }) =>
    http.get<never, PageResult<AdminQuestion>>('/admin/questions', { params }),

  get: (id: number) => http.get<never, AdminQuestion>(`/admin/questions/${id}`),

  create: (data: AdminQuestionRequest) =>
    http.post<never, AdminQuestion>('/admin/questions', data),

  update: (id: number, data: AdminQuestionRequest) =>
    http.put<never, AdminQuestion>(`/admin/questions/${id}`, data),

  remove: (id: number) => http.delete<never, void>(`/admin/questions/${id}`)
}
```

- [x] **Step 2: 创建管理员题库页**

Create `frontend/src/views/AdminQuestionsView.vue`:
```vue
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminQuestionApi, type AdminQuestion, type AdminQuestionRequest } from '@/api/interview'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const loading = ref(false)
const records = ref<AdminQuestion[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

const form = reactive<AdminQuestionRequest>({
  category: 'Java 基础',
  level: '校招',
  tags: '',
  difficulty: 1,
  question: '',
  referenceAnswer: '',
  source: '个人整理',
  status: 'ACTIVE'
})

const difficultyLabels: Record<number, string> = { 1: '简单', 2: '中等', 3: '困难' }

onMounted(async () => {
  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch {
      router.replace('/login')
      return
    }
  }
  if (auth.user?.role !== 'ADMIN') {
    ElMessage.warning('需要管理员权限')
    router.replace('/')
    return
  }
  await load()
})

async function load() {
  loading.value = true
  try {
    const data = await adminQuestionApi.page({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined
    })
    records.value = data.records
    total.value = data.total
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    category: 'Java 基础',
    level: '校招',
    tags: '',
    difficulty: 1,
    question: '',
    referenceAnswer: '',
    source: '个人整理',
    status: 'ACTIVE'
  })
  dialogVisible.value = true
}

function openEdit(row: AdminQuestion) {
  editingId.value = row.id
  Object.assign(form, {
    category: row.category,
    level: row.level,
    tags: row.tags.join(','),
    difficulty: row.difficulty,
    question: row.question,
    referenceAnswer: row.referenceAnswer,
    source: row.source ?? '',
    status: row.status
  })
  dialogVisible.value = true
}

async function submit() {
  if (!form.question.trim() || !form.referenceAnswer.trim() || !form.tags.trim()) {
    ElMessage.warning('题目、参考答案、标签不能为空')
    return
  }
  try {
    if (editingId.value) {
      await adminQuestionApi.update(editingId.value, { ...form })
    } else {
      await adminQuestionApi.create({ ...form })
    }
    ElMessage.success(editingId.value ? '题目已更新' : '题目已创建')
    dialogVisible.value = false
    await load()
  } catch {
    // 拦截器已提示
  }
}

async function remove(row: AdminQuestion) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.question}」吗？`, '删除题目', { type: 'warning' })
    await adminQuestionApi.remove(row.id)
    ElMessage.success('题目已删除')
    await load()
  } catch {
    // 用户取消或接口失败
  }
}

function search() {
  page.value = 1
  load()
}

function changePage(next: number) {
  page.value = next
  load()
}

function goHome() {
  router.push('/')
}
</script>

<template>
  <div class="admin-page">
    <header class="topbar">
      <button class="back-link" type="button" @click="goHome">← 返回岗位</button>
      <div class="brand">OfferPilot · 题库管理</div>
      <span class="status">ADMIN</span>
    </header>

    <main class="content">
      <section class="hero">
        <div>
          <p class="eyebrow">QUESTION BANK</p>
          <h1>题库管理</h1>
          <p class="subtitle">维护面试题目、参考答案和标签，供面试蓝图检索使用。</p>
        </div>
        <el-button type="primary" size="large" @click="openCreate">新增题目</el-button>
      </section>

      <section class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索题目 / 分类 / 标签"
          clearable
          style="max-width: 360px"
          @keyup.enter="search"
        />
        <el-button type="primary" @click="search">搜索</el-button>
      </section>

      <el-table v-loading="loading" :data="records" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="category" label="分类" width="130" />
        <el-table-column prop="question" label="题目" min-width="280" show-overflow-tooltip />
        <el-table-column label="标签" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="tag in row.tags" :key="tag" size="small" effect="plain" style="margin-right: 4px">
              {{ tag }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="难度" width="80">
          <template #default="{ row }">{{ difficultyLabels[row.difficulty] || row.difficulty }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="changePage"
        />
      </div>
    </main>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑题目' : '新增题目'" width="720px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="分类">
            <el-input v-model="form.category" />
          </el-form-item>
          <el-form-item label="适用级别">
            <el-input v-model="form.level" />
          </el-form-item>
          <el-form-item label="难度">
            <el-radio-group v-model="form.difficulty">
              <el-radio-button :value="1">简单</el-radio-button>
              <el-radio-button :value="2">中等</el-radio-button>
              <el-radio-button :value="3">困难</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="form.status">
              <el-radio-button value="ACTIVE">启用</el-radio-button>
              <el-radio-button value="INACTIVE">停用</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </div>
        <el-form-item label="标签（逗号分隔）">
          <el-input v-model="form.tags" placeholder="Java,HashMap,集合" />
        </el-form-item>
        <el-form-item label="题目">
          <el-input v-model="form.question" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="参考答案">
          <el-input v-model="form.referenceAnswer" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="来源">
          <el-input v-model="form.source" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-page {
  min-height: 100vh;
}

.topbar {
  height: 68px;
  padding: 0 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.9);
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
  max-width: 1200px;
  margin: 0 auto;
  padding: 36px 28px 72px;
}

.hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.eyebrow {
  margin: 0 0 10px;
  color: #2e75b6;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.hero h1 {
  margin: 0 0 8px;
  color: #17375e;
  font-size: clamp(28px, 3vw, 38px);
}

.subtitle {
  margin: 0;
  color: #7f8c9d;
  font-size: 14px;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 18px;
}

@media (max-width: 760px) {
  .topbar {
    padding: 0 18px;
  }

  .content {
    padding: 24px 16px 56px;
  }

  .hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
```

- [x] **Step 3: 添加路由与首页入口**

在 `frontend/src/router/index.ts` 加入：
```ts
    {
      path: '/admin/questions',
      name: 'admin-questions',
      component: () => import('@/views/AdminQuestionsView.vue'),
      meta: { requiresAuth: true }
    },
```

在 `HomeView.vue` 的 `.user-area` 中加入：
```vue
        <el-button
          v-if="auth.user?.role === 'ADMIN'"
          link
          type="primary"
          @click="router.push('/admin/questions')"
        >
          题库管理
        </el-button>
```

- [x] **Step 4: 创建面试弹窗显示额度**

在 `InterviewSetupDialog.vue`：
- 导入 `quotaApi, type QuotaView`：
```ts
import { quotaApi, type QuotaView } from '@/api/interview'
```
- 增加：
```ts
const quota = ref<QuotaView | null>(null)
```
- `loadResumes` 改为：
```ts
  try {
    const [resumeList, quotaData] = await Promise.all([
      resumeApi.list(),
      quotaApi.me()
    ])
    resumes.value = resumeList
    quota.value = quotaData
    if (!form.resumeId && resumes.value.length > 0) {
      form.resumeId = resumes.value[0].id
    }
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
```
- 在模式区块下方加入：
```vue
        <p v-if="quota" class="quota-line">
          今日剩余
          {{ quota.remainingSessions < 0 ? '不限' : quota.remainingSessions }}
          场
          <span v-if="quota.plan !== 'ADMIN'">（每日 {{ quota.sessionLimit }} 场）</span>
        </p>
```
- `submit` 开头加入：
```ts
  if (quota.value && quota.value.remainingSessions === 0) {
    ElMessage.warning('今日面试额度已用完，请明天再试')
    return
  }
```
- 样式：
```css
.quota-line {
  margin: 8px 0 0;
  color: #2e75b6;
  font-size: 12px;
}
```

- [x] **Step 5: 构建验证**

```bash
cd /e/work/frontend
npm run build
```

Expected: 构建成功。

- [x] **Step 6: 浏览器验收**

1. 用管理员登录：`admin@offerpilot.local / admin123`。
2. 首页出现“题库管理”，进入后新增一道题、搜索、编辑、删除。
3. 用普通用户登录，打开创建面试弹窗，应显示“今日剩余 3 场（每日 3 场）”。
4. 连续创建 3 场面试后，第 4 场应提示额度已用完/接口返回 429。

- [x] **Step 7: 更新 README**

在“当前进度”改为：
```markdown
当前进度：P0 Week 5 已完成（题库 RAG-lite、薄弱点推荐、管理员题库 CRUD、每日额度）。
```

在 API 表格中追加：
```markdown
| GET | `/api/quota/me` | 当前用户额度 |
| GET | `/api/admin/questions` | 管理员题库分页 |
| POST | `/api/admin/questions` | 管理员新增题目 |
| PUT | `/api/admin/questions/{id}` | 管理员编辑题目 |
| DELETE | `/api/admin/questions/{id}` | 管理员删除题目 |
```

增加管理员说明：
```markdown
## 管理员账号

- 邮箱：`admin@offerpilot.local`
- 密码：`admin123`
- 仅本地开发使用，部署前请修改密码或移除种子账号。
```

- [x] **Step 8: 提交**

```bash
cd /e/work
git add frontend/src/api/interview.ts frontend/src/views/AdminQuestionsView.vue \
  frontend/src/router/index.ts frontend/src/views/HomeView.vue \
  frontend/src/components/InterviewSetupDialog.vue README.md
git commit -m "feat(frontend): add admin question management and quota display"
```

---

## 自检结果

- **Spec coverage:** 管理员账号/题库 CRUD/额度扣减/额度查询/前端管理页/额度提示均覆盖；支付、Redis 分布式限流不做。
- **Placeholder scan:** 没有 TBD/TODO；代码完整。
- **Type consistency:** `QuotaView`、`AdminQuestion`、`AdminQuestionRequest`、`PageResult` 前后端字段一致；`InterviewService` 创建面试时扣减额度。
- **默认可用性:** 管理员种子账号可用；普通用户默认每日 3 场；额度数据落 MySQL/H2。
