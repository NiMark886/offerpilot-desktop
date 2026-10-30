# OfferPilot P0 Week 1 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建 OfferPilot 的可运行骨架：后端提供注册、登录、刷新令牌、当前用户和岗位模板 API；前端提供登录/注册页与岗位选择首页；测试、H2 本地开发环境和 MySQL 配置可用。

**Architecture:** 单仓库 `backend` + `frontend`。后端是 Spring Boot 3.5 模块化单体，按业务域分包（`user`、`job`、`common`），默认 `local` profile 使用 H2 + Flyway，`mysql` profile 连接 MySQL。JWT 无状态鉴权，refresh token 落库并支持轮换。前端 Vue3 + Vite + TypeScript + Element Plus + Pinia，通过 Vite proxy 调用后端。

**Tech Stack:** Java 21、Spring Boot 3.5.16、Spring Security 6、MyBatis-Plus 3.5.18、Flyway、H2/MySQL 8、jjwt 0.12.7、springdoc 2.8.17、Vue 3.5、Vite 8、TypeScript 5.9、Element Plus 2.14、Pinia 4、Axios 1.20。

---

## 0. 范围与完成定义

**本计划只覆盖 P0 第 1 周（W1）**，对应方案文档 5.1 的 W1：工程脚手架、数据库设计、登录注册、岗位模板。

**完成定义（Definition of Done）：**

- `mvn -f backend/pom.xml test` 全部通过。
- 后端可用 `local` profile 直接启动，不依赖 Docker。
- `POST /api/auth/register`、`POST /api/auth/login`、`POST /api/auth/refresh`、`POST /api/auth/logout`、`GET /api/auth/me` 可用。
- `GET /api/job-templates` 返回 4 条种子岗位。
- `GET /v3/api-docs` 与 Swagger UI 可访问。
- 前端 `npm run build` 通过；`npm run dev` 后可注册、登录、看到岗位卡片、退出。
- 代码已提交到 git，工作区干净。

**本计划不做：** 简历上传解析、面试会话、AI 调用、SSE、RAG、Redis、PostgreSQL、MinIO 业务接入。`docker-compose.yml` 只准备基础设施，W1 后端默认不依赖它。

**后续计划（不在本计划内）：**

- W2：简历上传/解析、创建面试、面试蓝图。
- W3：多轮对话、SSE、追问/提示/跳过、状态机。
- W4：单题评估、五维报告、历史记录。
- W5：题库 RAG、薄弱点推荐、后台、额度限流。
- W6：联调、压测、Docker 部署、演示与复盘。

---

## 1. 文件结构

```text
E:\work
├── .gitignore
├── .env.example
├── README.md
├── docker-compose.yml
├── OfferPilot-智能AI面试问答项目总体方案-V1.0.docx   # 已存在，不修改
├── docs/
│   └── superpowers/plans/2026-10-26-offerpilot-p0-week1.md
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/offerpilot/
│       │   ├── OfferPilotApplication.java
│       │   ├── common/
│       │   │   ├── api/ApiResponse.java
│       │   │   ├── config/MybatisPlusConfig.java
│       │   │   ├── config/OpenApiConfig.java
│       │   │   ├── exception/BusinessException.java
│       │   │   ├── exception/ErrorCode.java
│       │   │   ├── exception/GlobalExceptionHandler.java
│       │   │   ├── security/JwtAuthenticationFilter.java
│       │   │   ├── security/JwtService.java
│       │   │   ├── security/SecurityConfig.java
│       │   │   └── web/TraceIdFilter.java
│       │   ├── user/
│       │   │   ├── domain/User.java
│       │   │   ├── domain/RefreshToken.java
│       │   │   ├── mapper/UserMapper.java
│       │   │   ├── mapper/RefreshTokenMapper.java
│       │   │   ├── service/AuthService.java
│       │   │   ├── service/RefreshTokenService.java
│       │   │   ├── web/AuthController.java
│       │   │   └── web/dto/LoginRequest.java
│       │   │   └── web/dto/RefreshRequest.java
│       │   │   └── web/dto/RegisterRequest.java
│       │   │   └── web/dto/TokenResponse.java
│       │   │   └── web/dto/UserView.java
│       │   ├── job/
│       │   │   ├── domain/JobTemplate.java
│       │   │   ├── mapper/JobTemplateMapper.java
│       │   │   ├── service/JobTemplateService.java
│       │   │   ├── web/JobTemplateController.java
│       │   │   └── web/dto/JobTemplateView.java
│       │   └── ...
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── application-local.yml
│       │   ├── application-mysql.yml
│       │   └── db/migration/
│       │       ├── V1__init.sql
│       │       └── V2__seed_job_templates.sql
│       └── test/
│           ├── java/com/offerpilot/
│           │   ├── ApplicationContextIT.java
│           │   ├── common/exception/GlobalExceptionHandlerTest.java
│           │   ├── user/JwtServiceTest.java
│           │   ├── user/RefreshTokenServiceTest.java
│           │   ├── user/AuthControllerIT.java
│           │   └── job/JobTemplateControllerIT.java
│           └── resources/application-test.yml
└── frontend/
    ├── package.json
    ├── vite.config.ts
    ├── tsconfig.json
    ├── tsconfig.node.json
    ├── index.html
    └── src/
        ├── main.ts
        ├── App.vue
        ├── env.d.ts
        ├── api/http.ts
        ├── api/auth.ts
        ├── api/job.ts
        ├── router/index.ts
        ├── stores/auth.ts
        ├── styles/main.css
        └── views/LoginView.vue
        └── views/HomeView.vue
```

> 说明：`user` 域同时放认证与 token，是为了 W1 保持简单；W2 若变复杂，再拆 `auth` 模块。

---

## Task 1: 初始化 monorepo 与开发基础设施

**Files:**
- Create: `.gitignore`
- Create: `.env.example`
- Create: `docker-compose.yml`
- Create: `README.md`

- [ ] **Step 1: 初始化 git 仓库**

Run:
```bash
cd /e/work
git init -b main
```

Expected: `Initialized empty Git repository in E:/work/.git/`

- [ ] **Step 2: 创建 `.gitignore`**

Create `.gitignore`:
```gitignore
# Java / Maven
target/
*.class
*.log
*.jar
!.mvn/wrapper/maven-wrapper.jar

# IDE
.idea/
*.iml
.vscode/
*.swp

# Node
node_modules/
dist/
.vite/

# Environment
.env
.env.local
.env.*.local

# OS
.DS_Store
Thumbs.db

# H2
*.mv.db
*.trace.db
```

- [ ] **Step 3: 创建 `.env.example`**

Create `.env.example`:
```dotenv
# Backend
JWT_SECRET=replace-with-a-base64-encoded-64-byte-secret
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=offerpilot
MYSQL_USER=offerpilot
MYSQL_PASSWORD=offerpilot

# AI providers (W2+)
DEEPSEEK_API_KEY=sk-replace-me
EMBEDDING_API_KEY=replace-me
```

- [ ] **Step 4: 创建 `docker-compose.yml`**

Create `docker-compose.yml`（仅基础设施；W1 后端默认用 H2，不需要启动它）:
```yaml
name: offerpilot

services:
  mysql:
    image: mysql:8.4
    container_name: offerpilot-mysql
    environment:
      MYSQL_DATABASE: offerpilot
      MYSQL_USER: offerpilot
      MYSQL_PASSWORD: offerpilot
      MYSQL_ROOT_PASSWORD: root
      TZ: Asia/Shanghai
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uofferpilot", "-pofferpilot"]
      interval: 5s
      timeout: 3s
      retries: 20

  redis:
    image: redis:7.4-alpine
    container_name: offerpilot-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data

  postgres:
    image: pgvector/pgvector:pg16
    container_name: offerpilot-postgres
    environment:
      POSTGRES_DB: offerpilot
      POSTGRES_USER: offerpilot
      POSTGRES_PASSWORD: offerpilot
      TZ: Asia/Shanghai
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data

  minio:
    image: minio/minio:latest
    container_name: offerpilot-minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: offerpilot
      MINIO_ROOT_PASSWORD: offerpilot123
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio-data:/data

volumes:
  mysql-data:
  redis-data:
  postgres-data:
  minio-data:
```

- [ ] **Step 5: 创建初始 `README.md`**

Create `README.md`:
```markdown
# OfferPilot

面向 Java 后端 / Java+AI 求职者的垂直 AI 模拟面试教练。

当前进度：P0 Week 1（工程骨架、认证、岗位模板）。

## 目录

- `backend/`：Spring Boot 3.5 + MyBatis-Plus + Flyway
- `frontend/`：Vue3 + Vite + TypeScript + Element Plus
- `docs/`：实施计划
- `OfferPilot-智能AI面试问答项目总体方案-V1.0.docx`：产品与技术方案

## 快速开始

### 后端（H2，默认，不需要 Docker）

```bash
cd backend
mvn spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 Console: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:offerpilot`
  - User: `sa`，Password 留空

### 后端（MySQL）

先启动 Docker Desktop，然后：

```bash
docker compose up -d mysql
cd backend
SPRING_PROFILES_ACTIVE=mysql mvn spring-boot:run
```

PowerShell:
```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"; mvn spring-boot:run
```

### 前端

```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173。

### 测试

```bash
cd backend
mvn test
```

## 当前 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 注册并返回令牌 |
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/refresh` | 刷新令牌（轮换） |
| POST | `/api/auth/logout` | 注销 refresh token |
| GET | `/api/auth/me` | 当前用户 |
| GET | `/api/job-templates` | 岗位模板列表 |

## 开发约定

- Java 21，包名 `com.offerpilot`。
- 统一响应：`{ code, message, data, traceId }`，`code = 0` 表示成功。
- 时间字段使用 `LocalDateTime`，数据库用 `DATETIME(3)`。
- 所有表使用逻辑删除 `deleted`（0/1）。
- 每个任务完成后提交一次 git commit。
```

- [ ] **Step 6: 验证并提交**

Run:
```bash
cd /e/work
git status --short
docker compose config >/dev/null
git add .gitignore .env.example docker-compose.yml README.md
git commit -m "chore: initialize OfferPilot monorepo and dev infrastructure"
```

Expected: `docker compose config` 无报错；commit 成功。

---

## Task 2: 后端 Maven 工程骨架与配置

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/offerpilot/OfferPilotApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-local.yml`
- Create: `backend/src/main/resources/application-mysql.yml`
- Create: `backend/src/test/resources/application-test.yml`

- [ ] **Step 1: 创建 `backend/pom.xml`**

Create `backend/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.16</version>
        <relativePath/>
    </parent>

    <groupId>com.offerpilot</groupId>
    <artifactId>offerpilot-backend</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>offerpilot-backend</name>
    <description>OfferPilot AI interview backend</description>

    <properties>
        <java.version>21</java.version>
        <mybatis-plus.version>3.5.18</mybatis-plus.version>
        <jjwt.version>0.12.7</jjwt.version>
        <springdoc.version>2.8.17</springdoc.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-jsqlparser</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>

        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建启动类**

Create `backend/src/main/java/com/offerpilot/OfferPilotApplication.java`:
```java
package com.offerpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OfferPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(OfferPilotApplication.class, args);
    }
}
```

- [ ] **Step 3: 创建 `application.yml`**

Create `backend/src/main/resources/application.yml`:
```yaml
server:
  port: 8080

spring:
  application:
    name: offerpilot
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}
  autoconfigure:
    exclude: org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
  flyway:
    enabled: true
    locations: classpath:db/migration
  jackson:
    default-property-inclusion: non_null
    time-zone: Asia/Shanghai

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

springdoc:
  swagger-ui:
    path: /swagger-ui.html

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always

app:
  jwt:
    secret: ${JWT_SECRET:SrRRpFGv51wnxllAChZ6Ql1Bty5dvWIGIzbde9nqSxwIkjryZELF+htOBHGb6pX7jHjF0JHXoJUOyRtO7YsStQ==}
    access-token-ttl: PT2H
    refresh-token-ttl: P7D

logging:
  pattern:
    level: "%5p [%X{traceId:-}]"
```

- [ ] **Step 4: 创建 `application-local.yml`**

Create `backend/src/main/resources/application-local.yml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:offerpilot;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
```

- [ ] **Step 5: 创建 `application-mysql.yml`**

Create `backend/src/main/resources/application-mysql.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:offerpilot}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: ${MYSQL_USER:offerpilot}
    password: ${MYSQL_PASSWORD:offerpilot}
  h2:
    console:
      enabled: false
```

- [ ] **Step 6: 创建 `application-test.yml`**

Create `backend/src/test/resources/application-test.yml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:offerpilot_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: false
```

- [ ] **Step 7: 编译验证**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -DskipTests package
```

Expected: `BUILD SUCCESS`。首次执行会下载依赖，可能需要几分钟。

- [ ] **Step 8: 提交**

Run:
```bash
cd /e/work
git add backend/pom.xml backend/src/main/java/com/offerpilot/OfferPilotApplication.java \
  backend/src/main/resources/application.yml backend/src/main/resources/application-local.yml \
  backend/src/main/resources/application-mysql.yml backend/src/test/resources/application-test.yml
git commit -m "chore(backend): bootstrap Spring Boot 3.5 project"
```

---

## Task 3: 数据库迁移与种子数据

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__init.sql`
- Create: `backend/src/main/resources/db/migration/V2__seed_job_templates.sql`
- Test: `backend/src/test/java/com/offerpilot/ApplicationContextIT.java`

- [ ] **Step 1: 先写失败测试**

Create `backend/src/test/java/com/offerpilot/ApplicationContextIT.java`:
```java
package com.offerpilot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsTablesAndSeedsJobTemplates() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM job_template", Integer.class);
        assertThat(count).isEqualTo(4);
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=ApplicationContextIT test
```

Expected: FAIL，错误信息包含 `Table "JOB_TEMPLATE" not found` 或 Flyway 未找到迁移。

- [ ] **Step 3: 创建 V1 迁移**

Create `backend/src/main/resources/db/migration/V1__init.sql`:
```sql
CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(128) NULL,
    phone VARCHAR(32) NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE UNIQUE INDEX uk_sys_user_email ON sys_user(email);
CREATE UNIQUE INDEX uk_sys_user_phone ON sys_user(phone);

CREATE TABLE user_refresh_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    jti VARCHAR(64) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL
);

CREATE UNIQUE INDEX uk_refresh_token_jti ON user_refresh_token(jti);
CREATE INDEX idx_refresh_token_user_id ON user_refresh_token(user_id);

CREATE TABLE job_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    level VARCHAR(32) NOT NULL,
    category VARCHAR(64) NOT NULL,
    jd_text TEXT NOT NULL,
    config_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE INDEX idx_job_template_status_sort ON job_template(status, sort_order);
```

- [ ] **Step 4: 创建 V2 种子数据**

Create `backend/src/main/resources/db/migration/V2__seed_job_templates.sql`:
```sql
INSERT INTO job_template (title, level, category, jd_text, config_json, status, sort_order, created_at, updated_at)
VALUES
('Java 后端开发（校招）', '校招', 'Java 后端',
 '负责公司核心业务系统的后端开发，参与需求分析、接口设计、编码与测试。要求熟悉 Java 基础、集合、并发、JVM、MySQL、Redis，具备良好的编码习惯与学习能力。',
 '{"basic":30,"project":35,"scenario":20,"ai":15}',
 'ACTIVE', 10, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 后端开发（1–3 年）', '1-3 年', 'Java 后端',
 '负责高并发业务系统的设计与开发，参与架构优化、性能调优与故障排查。要求熟悉 Spring Boot、MyBatis、MySQL、Redis、消息队列，能够独立负责模块并讲清技术取舍。',
 '{"basic":20,"project":40,"scenario":25,"ai":15}',
 'ACTIVE', 20, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java + AI 应用开发', '1-3 年', 'AI 应用',
 '负责 LLM 应用的设计与落地，包括 RAG、Agent、提示词工程、向量检索与效果评估。要求具备 Java 后端能力，理解大模型的能力边界、成本与安全风险。',
 '{"basic":20,"project":30,"scenario":20,"ai":30}',
 'ACTIVE', 30, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Spring Cloud 微服务开发', '1-3 年', '微服务',
 '负责微服务架构下的服务拆分、注册发现、配置中心、网关、服务调用与链路治理。要求熟悉 Spring Cloud Alibaba、Nacos、OpenFeign、Gateway，理解分布式系统的一致性、熔断与限流。',
 '{"basic":20,"project":35,"scenario":30,"ai":15}',
 'ACTIVE', 40, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));
```

- [ ] **Step 5: 运行测试，确认通过**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=ApplicationContextIT test
```

Expected: PASS。

- [ ] **Step 6: 提交**

Run:
```bash
cd /e/work
git add backend/src/main/resources/db/migration backend/src/test/java/com/offerpilot/ApplicationContextIT.java
git commit -m "feat(db): add core schema and job template seed data"
```

---

## Task 4: 通用 API 响应、异常与 TraceId

**Files:**
- Create: `backend/src/main/java/com/offerpilot/common/api/ApiResponse.java`
- Create: `backend/src/main/java/com/offerpilot/common/exception/ErrorCode.java`
- Create: `backend/src/main/java/com/offerpilot/common/exception/BusinessException.java`
- Create: `backend/src/main/java/com/offerpilot/common/exception/GlobalExceptionHandler.java`
- Create: `backend/src/main/java/com/offerpilot/common/web/TraceIdFilter.java`
- Test: `backend/src/test/java/com/offerpilot/common/exception/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: 先写失败测试**

Create `backend/src/test/java/com/offerpilot/common/exception/GlobalExceptionHandlerTest.java`:
```java
package com.offerpilot.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mapsBusinessExceptionToHttpStatusAndBody() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.message").value("邮箱已被注册"));
    }

    @RestController
    static class TestController {
        @GetMapping("/test/conflict")
        void conflict() {
            throw new BusinessException(ErrorCode.CONFLICT, "邮箱已被注册");
        }
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=GlobalExceptionHandlerTest test
```

Expected: 编译失败，`BusinessException` / `ErrorCode` 不存在。

- [ ] **Step 3: 创建 `ErrorCode`**

Create `backend/src/main/java/com/offerpilot/common/exception/ErrorCode.java`:
```java
package com.offerpilot.common.exception;

public enum ErrorCode {
    VALIDATION_ERROR(40001, "参数校验失败"),
    UNAUTHORIZED(40101, "未登录或登录已过期"),
    FORBIDDEN(40301, "无权访问"),
    CONFLICT(40901, "资源冲突"),
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

- [ ] **Step 4: 创建 `BusinessException`**

Create `backend/src/main/java/com/offerpilot/common/exception/BusinessException.java`:
```java
package com.offerpilot.common.exception;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
```

- [ ] **Step 5: 创建 `ApiResponse`**

Create `backend/src/main/java/com/offerpilot/common/api/ApiResponse.java`:
```java
package com.offerpilot.common.api;

import org.slf4j.MDC;

public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "OK", data, MDC.get("traceId"));
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "OK", null, MDC.get("traceId"));
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, MDC.get("traceId"));
    }
}
```

- [ ] **Step 6: 创建 `GlobalExceptionHandler`**

Create `backend/src/main/java/com/offerpilot/common/exception/GlobalExceptionHandler.java`:
```java
package com.offerpilot.common.exception;

import com.offerpilot.common.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case CONFLICT -> HttpStatus.CONFLICT;
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status)
                .body(ApiResponse.error(ex.getErrorCode().code(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(ErrorCode.VALIDATION_ERROR.message());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR.code(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.code(), ErrorCode.INTERNAL_ERROR.message()));
    }
}
```

- [ ] **Step 7: 创建 `TraceIdFilter`**

Create `backend/src/main/java/com/offerpilot/common/web/TraceIdFilter.java`:
```java
package com.offerpilot.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(TRACE_ID, traceId);
        response.setHeader("X-Trace-Id", traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
        }
    }
}
```

- [ ] **Step 8: 运行测试，确认通过**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=GlobalExceptionHandlerTest test
```

Expected: PASS。

- [ ] **Step 9: 提交**

Run:
```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/common backend/src/test/java/com/offerpilot/common
git commit -m "feat(common): add unified API response, error handling and trace id"
```

---

## Task 5: 用户与刷新令牌领域模型 + MyBatis-Plus 配置

**Files:**
- Create: `backend/src/main/java/com/offerpilot/user/domain/User.java`
- Create: `backend/src/main/java/com/offerpilot/user/domain/RefreshToken.java`
- Create: `backend/src/main/java/com/offerpilot/job/domain/JobTemplate.java`
- Create: `backend/src/main/java/com/offerpilot/user/mapper/UserMapper.java`
- Create: `backend/src/main/java/com/offerpilot/user/mapper/RefreshTokenMapper.java`
- Create: `backend/src/main/java/com/offerpilot/job/mapper/JobTemplateMapper.java`
- Create: `backend/src/main/java/com/offerpilot/common/config/MybatisPlusConfig.java`
- Test: `backend/src/test/java/com/offerpilot/user/UserMapperIT.java`

- [ ] **Step 1: 先写失败测试**

Create `backend/src/test/java/com/offerpilot/user/UserMapperIT.java`:
```java
package com.offerpilot.user;

import com.offerpilot.user.domain.User;
import com.offerpilot.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UserMapperIT {

    @Autowired
    private UserMapper userMapper;

    @Test
    void insertsUserAndFillsAuditFields() {
        User user = new User();
        user.setEmail("mapper-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$placeholder");
        user.setNickname("Mapper Tester");
        user.setRole("USER");
        user.setStatus("ACTIVE");

        userMapper.insert(user);

        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    void logicDeleteHidesUser() {
        User user = new User();
        user.setEmail("delete-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$placeholder");
        user.setNickname("Delete Tester");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        userMapper.insert(user);

        userMapper.deleteById(user.getId());

        assertThat(userMapper.selectById(user.getId())).isNull();
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=UserMapperIT test
```

Expected: 编译失败，`User` / `UserMapper` 不存在。

- [ ] **Step 3: 创建 `User` 实体**

Create `backend/src/main/java/com/offerpilot/user/domain/User.java`:
```java
package com.offerpilot.user.domain;

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
@TableName("sys_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;

    private String phone;

    private String passwordHash;

    private String nickname;

    private String role;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
```

- [ ] **Step 4: 创建 `RefreshToken` 实体**

Create `backend/src/main/java/com/offerpilot/user/domain/RefreshToken.java`:
```java
package com.offerpilot.user.domain;

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
@TableName("user_refresh_token")
public class RefreshToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String jti;

    private String tokenHash;

    private LocalDateTime expiresAt;

    private Integer revoked;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 5: 创建 `JobTemplate` 实体**

Create `backend/src/main/java/com/offerpilot/job/domain/JobTemplate.java`:
```java
package com.offerpilot.job.domain;

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
@TableName("job_template")
public class JobTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String level;

    private String category;

    private String jdText;

    private String configJson;

    private String status;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
```

- [ ] **Step 6: 创建三个 Mapper**

Create `backend/src/main/java/com/offerpilot/user/mapper/UserMapper.java`:
```java
package com.offerpilot.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerpilot.user.domain.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

Create `backend/src/main/java/com/offerpilot/user/mapper/RefreshTokenMapper.java`:
```java
package com.offerpilot.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerpilot.user.domain.RefreshToken;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {
}
```

Create `backend/src/main/java/com/offerpilot/job/mapper/JobTemplateMapper.java`:
```java
package com.offerpilot.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerpilot.job.domain.JobTemplate;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JobTemplateMapper extends BaseMapper<JobTemplate> {
}
```

- [ ] **Step 7: 创建 MyBatis-Plus 配置**

Create `backend/src/main/java/com/offerpilot/common/config/MybatisPlusConfig.java`:
```java
package com.offerpilot.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
```

- [ ] **Step 8: 运行测试，确认通过**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=UserMapperIT test
```

Expected: PASS。若 H2 对 `DATETIME(3)` 报错，将迁移脚本中的 `DATETIME(3)` 改为 `TIMESTAMP(3)`，并同步修改文档中的类型说明。

- [ ] **Step 9: 提交**

Run:
```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/user backend/src/main/java/com/offerpilot/job \
  backend/src/main/java/com/offerpilot/common/config/MybatisPlusConfig.java \
  backend/src/test/java/com/offerpilot/user/UserMapperIT.java
git commit -m "feat(user): add user, refresh token and job template persistence"
```

---

## Task 6: JWT 与刷新令牌服务

**Files:**
- Create: `backend/src/main/java/com/offerpilot/common/security/JwtService.java`
- Create: `backend/src/main/java/com/offerpilot/user/service/RefreshTokenService.java`
- Test: `backend/src/test/java/com/offerpilot/user/JwtServiceTest.java`
- Test: `backend/src/test/java/com/offerpilot/user/RefreshTokenServiceTest.java`

- [ ] **Step 1: 先写 JWT 失败测试**

Create `backend/src/test/java/com/offerpilot/user/JwtServiceTest.java`:
```java
package com.offerpilot.user;

import com.offerpilot.common.security.JwtService;
import com.offerpilot.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

    @Test
    void createsAndParsesAccessToken() {
        User user = new User();
        user.setId(1L);
        user.setRole("USER");
        user.setNickname("Tester");

        String token = jwtService.createAccessToken(user);
        Claims claims = jwtService.parse(token);

        assertThat(jwtService.isType(claims, "access")).isTrue();
        assertThat(jwtService.userId(claims)).isEqualTo(1L);
        assertThat(jwtService.role(claims)).isEqualTo("USER");
    }

    @Test
    void rejectsTamperedToken() {
        User user = new User();
        user.setId(1L);
        user.setRole("USER");
        user.setNickname("Tester");

        String token = jwtService.createAccessToken(user);

        assertThatThrownBy(() -> jwtService.parse(token + "x"))
                .isInstanceOf(JwtException.class);
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=JwtServiceTest test
```

Expected: 编译失败，`JwtService` 不存在。

- [ ] **Step 3: 创建 `JwtService`**

Create `backend/src/main/java/com/offerpilot/common/security/JwtService.java`:
```java
package com.offerpilot.common.security;

import com.offerpilot.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.access-token-ttl}") Duration accessTokenTtl,
                      @Value("${app.jwt.refresh-token-ttl}") Duration refreshTokenTtl) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public String createAccessToken(User user) {
        return createToken(user, "access", accessTokenTtl, UUID.randomUUID().toString());
    }

    public String createRefreshToken(User user, String jti) {
        return createToken(user, "refresh", refreshTokenTtl, jti);
    }

    private String createToken(User user, String type, Duration ttl, String jti) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .id(jti)
                .claim("type", type)
                .claim("role", user.getRole())
                .claim("nickname", user.getNickname())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isType(Claims claims, String type) {
        return type.equals(claims.get("type", String.class));
    }

    public Long userId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String role(Claims claims) {
        return claims.get("role", String.class);
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    public Duration refreshTokenTtl() {
        return refreshTokenTtl;
    }
}
```

- [ ] **Step 4: 先写刷新令牌失败测试**

Create `backend/src/test/java/com/offerpilot/user/RefreshTokenServiceTest.java`:
```java
package com.offerpilot.user;

import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.security.JwtService;
import com.offerpilot.user.domain.User;
import com.offerpilot.user.mapper.UserMapper;
import com.offerpilot.user.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RefreshTokenServiceTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtService jwtService;

    @Test
    void issuesAndRevokesRefreshToken() {
        User user = createUser(userMapper);

        String token = refreshTokenService.issue(user);
        assertThat(jwtService.isType(jwtService.parse(token), "refresh")).isTrue();

        Long userId = refreshTokenService.verifyAndRevoke(token);
        assertThat(userId).isEqualTo(user.getId());

        assertThatThrownBy(() -> refreshTokenService.verifyAndRevoke(token))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新令牌");
    }

    private User createUser(UserMapper mapper) {
        User user = new User();
        user.setEmail("refresh-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$placeholder");
        user.setNickname("Refresh Tester");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        mapper.insert(user);
        return user;
    }
}
```

- [ ] **Step 5: 运行测试，确认失败**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=RefreshTokenServiceTest test
```

Expected: 编译失败，`RefreshTokenService` 不存在。

- [ ] **Step 6: 创建 `RefreshTokenService`**

Create `backend/src/main/java/com/offerpilot/user/service/RefreshTokenService.java`:
```java
package com.offerpilot.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.common.security.JwtService;
import com.offerpilot.user.domain.RefreshToken;
import com.offerpilot.user.domain.User;
import com.offerpilot.user.mapper.RefreshTokenMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenMapper refreshTokenMapper;
    private final JwtService jwtService;

    public RefreshTokenService(RefreshTokenMapper refreshTokenMapper, JwtService jwtService) {
        this.refreshTokenMapper = refreshTokenMapper;
        this.jwtService = jwtService;
    }

    public String issue(User user) {
        String jti = UUID.randomUUID().toString();
        String token = jwtService.createRefreshToken(user, jti);

        RefreshToken record = new RefreshToken();
        record.setUserId(user.getId());
        record.setJti(jti);
        record.setTokenHash(sha256(token));
        record.setExpiresAt(LocalDateTime.now().plus(jwtService.refreshTokenTtl()));
        record.setRevoked(0);
        refreshTokenMapper.insert(record);

        return token;
    }

    public Long verifyAndRevoke(String token) {
        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "刷新令牌无效或已过期");
        }

        if (!jwtService.isType(claims, "refresh")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "刷新令牌类型不正确");
        }

        String jti = claims.getId();
        RefreshToken record = refreshTokenMapper.selectOne(
                new LambdaQueryWrapper<RefreshToken>().eq(RefreshToken::getJti, jti));

        if (record == null
                || record.getRevoked() == null
                || record.getRevoked() == 1
                || record.getExpiresAt() == null
                || record.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "刷新令牌无效或已过期");
        }

        String actualHash = sha256(token);
        if (!MessageDigest.isEqual(
                record.getTokenHash().getBytes(StandardCharsets.UTF_8),
                actualHash.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "刷新令牌无效或已过期");
        }

        record.setRevoked(1);
        refreshTokenMapper.updateById(record);

        return record.getUserId();
    }

    public void revoke(String token) {
        try {
            verifyAndRevoke(token);
        } catch (RuntimeException ignored) {
            // 注销是幂等操作：令牌无效时也返回成功，避免泄露令牌状态
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
```

- [ ] **Step 7: 运行测试，确认通过**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=JwtServiceTest,RefreshTokenServiceTest test
```

Expected: PASS。

- [ ] **Step 8: 提交**

Run:
```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/common/security/JwtService.java \
  backend/src/main/java/com/offerpilot/user/service/RefreshTokenService.java \
  backend/src/test/java/com/offerpilot/user/JwtServiceTest.java \
  backend/src/test/java/com/offerpilot/user/RefreshTokenServiceTest.java
git commit -m "feat(auth): add JWT and rotating refresh token service"
```

---

## Task 7: 安全配置与认证 API

**Files:**
- Create: `backend/src/main/java/com/offerpilot/common/security/JwtAuthenticationFilter.java`
- Create: `backend/src/main/java/com/offerpilot/common/security/SecurityConfig.java`
- Create: `backend/src/main/java/com/offerpilot/user/web/dto/RegisterRequest.java`
- Create: `backend/src/main/java/com/offerpilot/user/web/dto/LoginRequest.java`
- Create: `backend/src/main/java/com/offerpilot/user/web/dto/RefreshRequest.java`
- Create: `backend/src/main/java/com/offerpilot/user/web/dto/UserView.java`
- Create: `backend/src/main/java/com/offerpilot/user/web/dto/TokenResponse.java`
- Create: `backend/src/main/java/com/offerpilot/user/service/AuthService.java`
- Create: `backend/src/main/java/com/offerpilot/user/web/AuthController.java`
- Test: `backend/src/test/java/com/offerpilot/user/AuthControllerIT.java`

- [ ] **Step 1: 先写失败测试**

Create `backend/src/test/java/com/offerpilot/user/AuthControllerIT.java`:
```java
package com.offerpilot.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.user.web.dto.LoginRequest;
import com.offerpilot.user.web.dto.RefreshRequest;
import com.offerpilot.user.web.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerLoginRefreshAndMe() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String password = "password123";

        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, password, "Tester"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode tokens = objectMapper.readTree(registered.getResponse().getContentAsString()).get("data");
        String accessToken = tokens.get("accessToken").asText();
        String refreshToken = tokens.get("refreshToken").asText();

        mockMvc.perform(get("/api/auth/me")
                        .header(AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        String newRefreshToken = objectMapper.readTree(refreshed.getResponse().getContentAsString())
                .get("data").get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(newRefreshToken))))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.com";
        String body = objectMapper.writeValueAsString(
                new RegisterRequest(email, "password123", "Tester"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        String email = "wrong-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, "password123", "Tester"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(email, "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=AuthControllerIT test
```

Expected: 编译失败，认证相关类不存在。

- [ ] **Step 3: 创建 DTO**

Create `backend/src/main/java/com/offerpilot/user/web/dto/RegisterRequest.java`:
```java
package com.offerpilot.user.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 128) String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Size(max = 64) String nickname
) {
}
```

Create `backend/src/main/java/com/offerpilot/user/web/dto/LoginRequest.java`:
```java
package com.offerpilot.user.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 128) String account,
        @NotBlank String password
) {
}
```

Create `backend/src/main/java/com/offerpilot/user/web/dto/RefreshRequest.java`:
```java
package com.offerpilot.user.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank String refreshToken) {
}
```

Create `backend/src/main/java/com/offerpilot/user/web/dto/UserView.java`:
```java
package com.offerpilot.user.web.dto;

import com.offerpilot.user.domain.User;

public record UserView(Long id, String nickname, String email, String role) {

    public static UserView from(User user) {
        return new UserView(user.getId(), user.getNickname(), user.getEmail(), user.getRole());
    }
}
```

Create `backend/src/main/java/com/offerpilot/user/web/dto/TokenResponse.java`:
```java
package com.offerpilot.user.web.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType,
        UserView user
) {
}
```

- [ ] **Step 4: 创建 `AuthService`**

Create `backend/src/main/java/com/offerpilot/user/service/AuthService.java`:
```java
package com.offerpilot.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.common.security.JwtService;
import com.offerpilot.user.domain.User;
import com.offerpilot.user.mapper.UserMapper;
import com.offerpilot.user.web.dto.LoginRequest;
import com.offerpilot.user.web.dto.RefreshRequest;
import com.offerpilot.user.web.dto.RegisterRequest;
import com.offerpilot.user.web.dto.TokenResponse;
import com.offerpilot.user.web.dto.UserView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Long existing = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (existing > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "邮箱已被注册");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname().trim());
        user.setRole("USER");
        user.setStatus("ACTIVE");

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "邮箱已被注册");
        }

        return issueTokens(user);
    }

    public TokenResponse login(LoginRequest request) {
        String account = request.account().trim();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, account.toLowerCase(Locale.ROOT))
                .or()
                .eq(User::getPhone, account)
                .last("LIMIT 1"));

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        Long userId = refreshTokenService.verifyAndRevoke(request.refreshToken());
        User user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在或已被禁用");
        }
        return issueTokens(user);
    }

    public void logout(RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    public UserView me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }
        return UserView.from(user);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtService.createAccessToken(user);
        String refreshToken = refreshTokenService.issue(user);
        return new TokenResponse(
                accessToken,
                refreshToken,
                jwtService.accessTokenTtl().toSeconds(),
                "Bearer",
                UserView.from(user));
    }
}
```

- [ ] **Step 5: 创建 `JwtAuthenticationFilter`**

Create `backend/src/main/java/com/offerpilot/common/security/JwtAuthenticationFilter.java`:
```java
package com.offerpilot.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                if (jwtService.isType(claims, "access")) {
                    Long userId = jwtService.userId(claims);
                    String role = jwtService.role(claims);
                    String authority = "ROLE_" + (role == null ? "USER" : role);
                    var authentication = new UsernamePasswordAuthenticationToken(
                            userId, null, List.of(new SimpleGrantedAuthority(authority)));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 6: 创建 `SecurityConfig`**

Create `backend/src/main/java/com/offerpilot/common/security/SecurityConfig.java`:
```java
package com.offerpilot.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.common.exception.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   ObjectMapper objectMapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/job-templates").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/h2-console/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, objectMapper, HttpStatus.UNAUTHORIZED,
                                        ErrorCode.UNAUTHORIZED, "未登录或登录已过期"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, objectMapper, HttpStatus.FORBIDDEN,
                                        ErrorCode.FORBIDDEN, "无权访问")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static void writeError(HttpServletResponse response,
                                   ObjectMapper objectMapper,
                                   HttpStatus status,
                                   ErrorCode code,
                                   String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(code.code(), message));
    }
}
```

- [ ] **Step 7: 创建 `AuthController`**

Create `backend/src/main/java/com/offerpilot/user/web/AuthController.java`:
```java
package com.offerpilot.user.web;

import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.user.service.AuthService;
import com.offerpilot.user.web.dto.LoginRequest;
import com.offerpilot.user.web.dto.RefreshRequest;
import com.offerpilot.user.web.dto.RegisterRequest;
import com.offerpilot.user.web.dto.TokenResponse;
import com.offerpilot.user.web.dto.UserView;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(authService.me(userId));
    }
}
```

- [ ] **Step 8: 运行测试，确认通过**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=AuthControllerIT test
```

Expected: PASS。若失败，优先检查：
- H2 迁移是否成功。
- `JWT_SECRET` 是否为合法 Base64 且长度 ≥ 64 字节。
- `AuthControllerIT` 是否使用了 `@ActiveProfiles("test")`。

- [ ] **Step 9: 提交**

Run:
```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/common/security \
  backend/src/main/java/com/offerpilot/user/web backend/src/main/java/com/offerpilot/user/service/AuthService.java \
  backend/src/test/java/com/offerpilot/user/AuthControllerIT.java
git commit -m "feat(auth): add register, login, refresh, logout and me APIs"
```

---

## Task 8: 岗位模板 API

**Files:**
- Create: `backend/src/main/java/com/offerpilot/job/web/dto/JobTemplateView.java`
- Create: `backend/src/main/java/com/offerpilot/job/service/JobTemplateService.java`
- Create: `backend/src/main/java/com/offerpilot/job/web/JobTemplateController.java`
- Test: `backend/src/test/java/com/offerpilot/job/JobTemplateControllerIT.java`

- [ ] **Step 1: 先写失败测试**

Create `backend/src/test/java/com/offerpilot/job/JobTemplateControllerIT.java`:
```java
package com.offerpilot.job;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobTemplateControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsFourSeededJobTemplatesWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/job-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].title").isNotEmpty());
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=JobTemplateControllerIT test
```

Expected: FAIL，404 或返回空。若 Security 拦截则返回 401。

- [ ] **Step 3: 创建 `JobTemplateView`**

Create `backend/src/main/java/com/offerpilot/job/web/dto/JobTemplateView.java`:
```java
package com.offerpilot.job.web.dto;

import com.offerpilot.job.domain.JobTemplate;

public record JobTemplateView(Long id, String title, String level, String category, String jdText) {

    public static JobTemplateView from(JobTemplate jobTemplate) {
        return new JobTemplateView(
                jobTemplate.getId(),
                jobTemplate.getTitle(),
                jobTemplate.getLevel(),
                jobTemplate.getCategory(),
                jobTemplate.getJdText());
    }
}
```

- [ ] **Step 4: 创建 `JobTemplateService`**

Create `backend/src/main/java/com/offerpilot/job/service/JobTemplateService.java`:
```java
package com.offerpilot.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.job.mapper.JobTemplateMapper;
import com.offerpilot.job.web.dto.JobTemplateView;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JobTemplateService {

    private final JobTemplateMapper jobTemplateMapper;

    public JobTemplateService(JobTemplateMapper jobTemplateMapper) {
        this.jobTemplateMapper = jobTemplateMapper;
    }

    public List<JobTemplateView> listActive() {
        return jobTemplateMapper.selectList(new LambdaQueryWrapper<JobTemplate>()
                        .eq(JobTemplate::getStatus, "ACTIVE")
                        .orderByAsc(JobTemplate::getSortOrder))
                .stream()
                .map(JobTemplateView::from)
                .toList();
    }
}
```

- [ ] **Step 5: 创建 `JobTemplateController`**

Create `backend/src/main/java/com/offerpilot/job/web/JobTemplateController.java`:
```java
package com.offerpilot.job.web;

import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.job.service.JobTemplateService;
import com.offerpilot.job.web.dto.JobTemplateView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/job-templates")
public class JobTemplateController {

    private final JobTemplateService jobTemplateService;

    public JobTemplateController(JobTemplateService jobTemplateService) {
        this.jobTemplateService = jobTemplateService;
    }

    @GetMapping
    public ApiResponse<List<JobTemplateView>> list() {
        return ApiResponse.ok(jobTemplateService.listActive());
    }
}
```

- [ ] **Step 6: 运行测试，确认通过**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=JobTemplateControllerIT test
```

Expected: PASS。

- [ ] **Step 7: 提交**

Run:
```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/job backend/src/test/java/com/offerpilot/job
git commit -m "feat(job): expose job template list API"
```

---

## Task 9: OpenAPI 与运行时验证

**Files:**
- Create: `backend/src/main/java/com/offerpilot/common/config/OpenApiConfig.java`

- [ ] **Step 1: 创建 `OpenApiConfig`**

Create `backend/src/main/java/com/offerpilot/common/config/OpenApiConfig.java`:
```java
package com.offerpilot.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI offerPilotOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("OfferPilot API")
                        .version("0.1.0")
                        .description("OfferPilot AI 面试平台 API"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
```

- [ ] **Step 2: 启动后端**

Run in a background terminal:
```bash
cd /e/work
mvn -f backend/pom.xml spring-boot:run
```

Expected log contains `Started OfferPilotApplication` and `Tomcat started on port 8080`.

- [ ] **Step 3: 验证公开端点**

Run:
```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/api/job-templates
curl -s http://localhost:8080/v3/api-docs | head -c 200
```

Expected:
- health 返回 `{"status":"UP"...}`。
- job-templates 返回 `code=0` 和 4 条数据。
- `/v3/api-docs` 返回包含 `"title":"OfferPilot API"` 的 JSON。

- [ ] **Step 4: 验证认证端点**

Run:
```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@example.com","password":"password123","nickname":"Demo"}'
```

Expected: 返回 `accessToken`、`refreshToken`、`user`。

- [ ] **Step 5: 提交**

Run:
```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/common/config/OpenApiConfig.java
git commit -m "docs(api): add OpenAPI configuration"
```

---

## Task 10: 前端脚手架与 API 层

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/index.html`
- Create: `frontend/src/main.ts`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/env.d.ts`
- Create: `frontend/src/api/http.ts`
- Create: `frontend/src/api/auth.ts`
- Create: `frontend/src/api/job.ts`
- Create: `frontend/src/router/index.ts`
- Create: `frontend/src/stores/auth.ts`
- Create: `frontend/src/styles/main.css`

- [ ] **Step 1: 创建 `package.json`**

Create `frontend/package.json`:
```json
{
  "name": "offerpilot-frontend",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc -b && vite build",
    "preview": "vite preview",
    "type-check": "vue-tsc --noEmit"
  },
  "dependencies": {
    "axios": "^1.20.0",
    "element-plus": "^2.14.7",
    "pinia": "^4.1.0",
    "vue": "^3.5.44",
    "vue-router": "^5.4.0"
  },
  "devDependencies": {
    "@types/node": "^26.6.5",
    "@vitejs/plugin-vue": "^6.0.10",
    "typescript": "^5.9.3",
    "vite": "^8.3.5",
    "vue-tsc": "^3.3.13"
  }
}
```

- [ ] **Step 2: 创建构建配置**

Create `frontend/vite.config.ts`:
```ts
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

Create `frontend/tsconfig.json`:
```json
{
  "compilerOptions": {
    "target": "ES2022",
    "useDefineForClassFields": true,
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "strict": true,
    "jsx": "preserve",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "esModuleInterop": true,
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "skipLibCheck": true,
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    }
  },
  "include": ["src/**/*.ts", "src/**/*.d.ts", "src/**/*.tsx", "src/**/*.vue"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

Create `frontend/tsconfig.node.json`:
```json
{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "allowSyntheticDefaultImports": true,
    "types": ["node"]
  },
  "include": ["vite.config.ts"]
}
```

Create `frontend/index.html`:
```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>OfferPilot · AI 面试教练</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

Create `frontend/src/env.d.ts`:
```ts
/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
```

- [ ] **Step 3: 创建入口与路由**

Create `frontend/src/main.ts`:
```ts
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/main.css'

createApp(App)
  .use(createPinia())
  .use(router)
  .use(ElementPlus)
  .mount('#app')
```

Create `frontend/src/App.vue`:
```vue
<template>
  <router-view />
</template>
```

Create `frontend/src/router/index.ts`:
```ts
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue')
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.isAuthenticated) {
    return { name: 'home' }
  }
  return true
})

export default router
```

- [ ] **Step 4: 创建 API 层**

Create `frontend/src/api/http.ts`:
```ts
import axios from 'axios'
import { ElMessage } from 'element-plus'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

const ACCESS_KEY = 'offerpilot_access_token'
const REFRESH_KEY = 'offerpilot_refresh_token'
const USER_KEY = 'offerpilot_user'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(ACCESS_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body.code !== 0) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body.data
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message

    if (status === 401) {
      localStorage.removeItem(ACCESS_KEY)
      localStorage.removeItem(REFRESH_KEY)
      localStorage.removeItem(USER_KEY)
      if (window.location.pathname !== '/login') {
        window.location.assign('/login')
      }
    }

    ElMessage.error(message || '网络异常，请稍后重试')
    return Promise.reject(error)
  }
)

export default http
```

Create `frontend/src/api/auth.ts`:
```ts
import http from './http'

export interface UserView {
  id: number
  nickname: string
  email: string
  role: string
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  tokenType: string
  user: UserView
}

export const authApi = {
  register: (data: { email: string; password: string; nickname: string }) =>
    http.post<never, TokenResponse>('/auth/register', data),

  login: (data: { account: string; password: string }) =>
    http.post<never, TokenResponse>('/auth/login', data),

  refresh: (refreshToken: string) =>
    http.post<never, TokenResponse>('/auth/refresh', { refreshToken }),

  logout: (refreshToken: string) =>
    http.post<never, void>('/auth/logout', { refreshToken }),

  me: () => http.get<never, UserView>('/auth/me')
}
```

Create `frontend/src/api/job.ts`:
```ts
import http from './http'

export interface JobTemplate {
  id: number
  title: string
  level: string
  category: string
  jdText: string
}

export const jobApi = {
  list: () => http.get<never, JobTemplate[]>('/job-templates')
}
```

- [ ] **Step 5: 创建 Pinia 认证 Store**

Create `frontend/src/stores/auth.ts`:
```ts
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi, type TokenResponse, type UserView } from '@/api/auth'

const ACCESS_KEY = 'offerpilot_access_token'
const REFRESH_KEY = 'offerpilot_refresh_token'
const USER_KEY = 'offerpilot_user'

function readStoredUser(): UserView | null {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null') as UserView | null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(localStorage.getItem(ACCESS_KEY) || '')
  const refreshToken = ref(localStorage.getItem(REFRESH_KEY) || '')
  const user = ref<UserView | null>(readStoredUser())

  const isAuthenticated = computed(() => Boolean(accessToken.value))

  function persist(tokens: TokenResponse) {
    accessToken.value = tokens.accessToken
    refreshToken.value = tokens.refreshToken
    user.value = tokens.user

    localStorage.setItem(ACCESS_KEY, tokens.accessToken)
    localStorage.setItem(REFRESH_KEY, tokens.refreshToken)
    localStorage.setItem(USER_KEY, JSON.stringify(tokens.user))
  }

  async function login(account: string, password: string) {
    const tokens = await authApi.login({ account, password })
    persist(tokens)
  }

  async function register(email: string, password: string, nickname: string) {
    const tokens = await authApi.register({ email, password, nickname })
    persist(tokens)
  }

  async function fetchMe() {
    user.value = await authApi.me()
    localStorage.setItem(USER_KEY, JSON.stringify(user.value))
  }

  async function logout() {
    try {
      if (refreshToken.value) {
        await authApi.logout(refreshToken.value)
      }
    } finally {
      accessToken.value = ''
      refreshToken.value = ''
      user.value = null
      localStorage.removeItem(ACCESS_KEY)
      localStorage.removeItem(REFRESH_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }

  return {
    accessToken,
    refreshToken,
    user,
    isAuthenticated,
    login,
    register,
    fetchMe,
    logout
  }
})
```

- [ ] **Step 6: 创建全局样式**

Create `frontend/src/styles/main.css`:
```css
:root {
  font-family: "Microsoft YaHei", "PingFang SC", system-ui, sans-serif;
  color: #1f2d3d;
  background: #f5f7fa;
  font-synthesis: none;
  text-rendering: optimizeLegibility;
}

* {
  box-sizing: border-box;
}

html,
body,
#app {
  min-height: 100%;
  margin: 0;
}

body {
  min-width: 320px;
  background:
    radial-gradient(circle at 10% 10%, rgba(64, 158, 255, 0.12), transparent 30%),
    radial-gradient(circle at 90% 20%, rgba(103, 194, 58, 0.08), transparent 28%),
    #f5f7fa;
}

a {
  color: inherit;
  text-decoration: none;
}

button {
  font-family: inherit;
}
```

- [ ] **Step 7: 安装依赖并构建**

Run:
```bash
cd /e/work/frontend
npm install
npm run build
```

Expected: `npm install` 成功；`npm run build` 输出 `✓ built in ...`，且 `dist/` 生成。

> 如果 `vue-tsc` 报 `Cannot find module '@/...'`，检查 `tsconfig.json` 的 `paths` 和 `vite.config.ts` 的 alias。

- [ ] **Step 8: 提交**

Run:
```bash
cd /e/work
git add frontend
git commit -m "feat(frontend): scaffold Vue3 app with auth store and API client"
```

---

## Task 11: 登录 / 注册页面

**Files:**
- Create: `frontend/src/views/LoginView.vue`

- [ ] **Step 1: 创建登录注册页**

Create `frontend/src/views/LoginView.vue`:
```vue
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

type Mode = 'login' | 'register'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const mode = ref<Mode>('login')
const loading = ref(false)
const formRef = ref<FormInstance>()

const loginForm = reactive({ account: '', password: '' })
const registerForm = reactive({ email: '', password: '', nickname: '' })

const activeForm = computed(() => (mode.value === 'login' ? loginForm : registerForm))

const loginRules: FormRules = {
  account: [{ required: true, message: '请输入邮箱', trigger: 'blur' }],
  password: [{ required: true, min: 8, message: '密码至少 8 位', trigger: 'blur' }]
}

const registerRules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入有效邮箱', trigger: 'blur' }
  ],
  password: [{ required: true, min: 8, message: '密码至少 8 位', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }]
}

const activeRules = computed<FormRules>(() => (mode.value === 'login' ? loginRules : registerRules))

function switchMode(next: Mode) {
  mode.value = next
  formRef.value?.clearValidate()
}

async function submit() {
  const form = formRef.value
  if (!form) return

  const valid = await form.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    if (mode.value === 'login') {
      await auth.login(loginForm.account, loginForm.password)
    } else {
      await auth.register(registerForm.email, registerForm.password, registerForm.nickname)
    }
    ElMessage.success(mode.value === 'login' ? '登录成功' : '注册成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.replace(redirect)
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <section class="brand-panel">
      <div class="brand-mark">OfferPilot</div>
      <h1>把一次真实的技术面试，<br />装进浏览器。</h1>
      <p>选岗位 · 传简历 · 多轮追问 · 五维评分 · 薄弱点复练</p>
      <div class="brand-points">
        <span>Java 后端</span>
        <span>Java + AI</span>
        <span>Spring Cloud</span>
      </div>
    </section>

    <section class="form-panel">
      <div class="form-card">
        <div class="mode-switch">
          <button
            :class="{ active: mode === 'login' }"
            type="button"
            @click="switchMode('login')"
          >
            登录
          </button>
          <button
            :class="{ active: mode === 'register' }"
            type="button"
            @click="switchMode('register')"
          >
            注册
          </button>
        </div>

        <h2>{{ mode === 'login' ? '欢迎回来' : '创建账号' }}</h2>
        <p class="hint">
          {{ mode === 'login' ? '登录后继续你的面试训练。' : '注册后即可开始第一场模拟面试。' }}
        </p>

        <el-form
          ref="formRef"
          :model="activeForm"
          :rules="activeRules"
          label-position="top"
          size="large"
          @submit.prevent="submit"
        >
          <template v-if="mode === 'login'">
            <el-form-item label="邮箱" prop="account">
              <el-input v-model="loginForm.account" placeholder="you@example.com" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                show-password
                placeholder="至少 8 位"
                @keyup.enter="submit"
              />
            </el-form-item>
          </template>

          <template v-else>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="registerForm.nickname" placeholder="怎么称呼你" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="registerForm.email" placeholder="you@example.com" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="registerForm.password"
                type="password"
                show-password
                placeholder="至少 8 位"
                @keyup.enter="submit"
              />
            </el-form-item>
          </template>

          <el-button
            class="submit-button"
            type="primary"
            :loading="loading"
            native-type="submit"
          >
            {{ mode === 'login' ? '登录' : '注册并开始' }}
          </el-button>
        </el-form>

        <p class="switch-line">
          {{ mode === 'login' ? '还没有账号？' : '已有账号？' }}
          <button type="button" @click="switchMode(mode === 'login' ? 'register' : 'login')">
            {{ mode === 'login' ? '立即注册' : '去登录' }}
          </button>
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  background: #f5f7fa;
}

.brand-panel {
  position: relative;
  padding: 72px 64px;
  color: #fff;
  background:
    radial-gradient(circle at 20% 20%, rgba(255, 255, 255, 0.16), transparent 34%),
    linear-gradient(140deg, #17375e 0%, #1f4e79 55%, #2e75b6 100%);
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow: hidden;
}

.brand-panel::after {
  content: '';
  position: absolute;
  right: -120px;
  bottom: -160px;
  width: 420px;
  height: 420px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.18);
}

.brand-mark {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.08em;
  opacity: 0.9;
  margin-bottom: 28px;
}

.brand-panel h1 {
  font-size: clamp(34px, 4vw, 52px);
  line-height: 1.2;
  margin: 0 0 20px;
  font-weight: 700;
}

.brand-panel p {
  font-size: 17px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.78);
  margin: 0;
}

.brand-points {
  display: flex;
  gap: 10px;
  margin-top: 36px;
  flex-wrap: wrap;
}

.brand-points span {
  padding: 7px 14px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  font-size: 13px;
  color: rgba(255, 255, 255, 0.9);
}

.form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 32px;
}

.form-card {
  width: 100%;
  max-width: 420px;
  padding: 40px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #e4ecf5;
  border-radius: 20px;
  box-shadow: 0 18px 50px rgba(31, 78, 121, 0.1);
  backdrop-filter: blur(10px);
}

.mode-switch {
  display: inline-flex;
  padding: 4px;
  background: #eef4fb;
  border-radius: 10px;
  margin-bottom: 28px;
}

.mode-switch button {
  border: 0;
  background: transparent;
  padding: 8px 22px;
  border-radius: 8px;
  cursor: pointer;
  color: #5b6b7f;
  font-size: 14px;
}

.mode-switch button.active {
  background: #fff;
  color: #1f4e79;
  font-weight: 700;
  box-shadow: 0 2px 8px rgba(31, 78, 121, 0.12);
}

.form-card h2 {
  margin: 0 0 8px;
  color: #17375e;
  font-size: 26px;
}

.hint {
  margin: 0 0 28px;
  color: #7f8c9d;
  font-size: 14px;
}

.submit-button {
  width: 100%;
  height: 46px;
  margin-top: 8px;
  font-size: 15px;
  border-radius: 10px;
}

.switch-line {
  margin: 22px 0 0;
  text-align: center;
  color: #7f8c9d;
  font-size: 13px;
}

.switch-line button {
  border: 0;
  background: transparent;
  color: #2e75b6;
  cursor: pointer;
  font-size: 13px;
  padding: 0;
}

@media (max-width: 860px) {
  .auth-page {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    padding: 48px 28px;
  }

  .brand-panel h1 {
    font-size: 32px;
  }

  .form-panel {
    padding: 28px 18px 48px;
  }

  .form-card {
    padding: 28px 22px;
  }
}
</style>
```

- [ ] **Step 2: 构建验证**

Run:
```bash
cd /e/work/frontend
npm run build
```

Expected: 构建成功。

- [ ] **Step 3: 手动验证**

1. 启动后端：`mvn -f backend/pom.xml spring-boot:run`。
2. 启动前端：`cd frontend && npm run dev`。
3. 打开 http://localhost:5173/login。
4. 点击“注册”，填写昵称、邮箱、密码，提交。
5. 注册成功后应跳转到 `/`（此时首页还没实现，可能空白，属正常）。

- [ ] **Step 4: 提交**

Run:
```bash
cd /e/work
git add frontend/src/views/LoginView.vue
git commit -m "feat(frontend): add login and register page"
```

---

## Task 12: 岗位选择首页

**Files:**
- Create: `frontend/src/views/HomeView.vue`

- [ ] **Step 1: 创建首页**

Create `frontend/src/views/HomeView.vue`:
```vue
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { jobApi, type JobTemplate } from '@/api/job'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const jobs = ref<JobTemplate[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    if (!auth.user) {
      await auth.fetchMe()
    }
    jobs.value = await jobApi.list()
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
})

async function handleLogout() {
  await auth.logout()
  router.replace({ name: 'login' })
}

function startInterview(job: JobTemplate) {
  ElMessage.info(`「${job.title}」面试功能将在下一阶段开放`)
}
</script>

<template>
  <div class="home-page">
    <header class="topbar">
      <div class="brand">OfferPilot</div>
      <div class="user-area">
        <span class="user-name">{{ auth.user?.nickname || '同学' }}</span>
        <el-button link type="primary" @click="handleLogout">退出登录</el-button>
      </div>
    </header>

    <main class="content">
      <section class="hero">
        <div>
          <p class="eyebrow">P0 · WEEK 1</p>
          <h1>选择你的目标岗位</h1>
          <p class="subtitle">
            AI 将根据岗位与简历生成一场带追问的模拟面试。先选一个方向，开始训练。
          </p>
        </div>
        <div class="hero-badge">
          <span>4</span>
          <small>岗位模板</small>
        </div>
      </section>

      <el-skeleton v-if="loading" :rows="5" animated />

      <section v-else class="job-grid">
        <article v-for="job in jobs" :key="job.id" class="job-card">
          <div class="job-meta">
            <span>{{ job.level }}</span>
            <span>{{ job.category }}</span>
          </div>
          <h2>{{ job.title }}</h2>
          <p class="job-desc">{{ job.jdText }}</p>
          <div class="card-footer">
            <el-button type="primary" @click="startInterview(job)">开始面试</el-button>
            <span class="coming">W2 开放</span>
          </div>
        </article>
      </section>

      <el-empty v-if="!loading && jobs.length === 0" description="暂无岗位模板" />
    </main>
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
}

.topbar {
  height: 68px;
  padding: 0 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.82);
  border-bottom: 1px solid #e4ecf5;
  backdrop-filter: blur(12px);
  position: sticky;
  top: 0;
  z-index: 10;
}

.brand {
  font-size: 20px;
  font-weight: 800;
  color: #17375e;
  letter-spacing: 0.04em;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-name {
  color: #5b6b7f;
  font-size: 14px;
}

.content {
  max-width: 1180px;
  margin: 0 auto;
  padding: 56px 32px 80px;
}

.hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 36px;
}

.eyebrow {
  margin: 0 0 12px;
  color: #2e75b6;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.hero h1 {
  margin: 0 0 12px;
  color: #17375e;
  font-size: clamp(30px, 4vw, 44px);
  line-height: 1.15;
}

.subtitle {
  margin: 0;
  max-width: 560px;
  color: #6b7a8c;
  font-size: 15px;
  line-height: 1.8;
}

.hero-badge {
  flex: none;
  width: 112px;
  height: 112px;
  border-radius: 24px;
  background: linear-gradient(145deg, #1f4e79, #2e75b6);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-shadow: 0 16px 30px rgba(31, 78, 121, 0.22);
}

.hero-badge span {
  font-size: 36px;
  font-weight: 800;
  line-height: 1;
}

.hero-badge small {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.82;
}

.job-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.job-card {
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #e4ecf5;
  border-radius: 18px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  min-height: 250px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.job-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 16px 34px rgba(31, 78, 121, 0.12);
}

.job-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.job-meta span {
  padding: 4px 10px;
  border-radius: 999px;
  background: #eef4fb;
  color: #2e75b6;
  font-size: 12px;
}

.job-card h2 {
  margin: 0 0 12px;
  color: #17375e;
  font-size: 20px;
  line-height: 1.4;
}

.job-desc {
  margin: 0;
  color: #6b7a8c;
  font-size: 14px;
  line-height: 1.75;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 22px;
}

.coming {
  color: #a0aab6;
  font-size: 12px;
}

@media (max-width: 720px) {
  .topbar {
    padding: 0 20px;
  }

  .content {
    padding: 36px 20px 60px;
  }

  .hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .hero-badge {
    display: none;
  }
}
</style>
```

- [ ] **Step 2: 构建验证**

Run:
```bash
cd /e/work/frontend
npm run build
```

Expected: 构建成功。

- [ ] **Step 3: 手动验证**

1. 后端运行在 8080。
2. 前端运行在 5173。
3. 注册或登录后进入首页。
4. 应看到 4 张岗位卡片：Java 后端校招、Java 后端 1–3 年、Java + AI 应用开发、Spring Cloud 微服务开发。
5. 点击“退出登录”应回到登录页。

- [ ] **Step 4: 提交**

Run:
```bash
cd /e/work
git add frontend/src/views/HomeView.vue
git commit -m "feat(frontend): add job selection home page"
```

---

## Task 13: 端到端验收与最终整理

**Files:**
- Modify: `README.md`

- [ ] **Step 1: 运行后端全量测试**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml test
```

Expected: 所有测试 PASS。

- [ ] **Step 2: 构建前端**

Run:
```bash
cd /e/work/frontend
npm run build
```

Expected: `✓ built in ...`。

- [ ] **Step 3: 端到端手工验收**

1. 启动后端（H2）：`mvn -f backend/pom.xml spring-boot:run`。
2. 启动前端：`cd frontend && npm run dev`。
3. 打开 http://localhost:5173/login。
4. 注册一个新账号。
5. 确认首页显示 4 个岗位。
6. 刷新页面，确认登录态保留。
7. 退出登录，确认回到登录页。
8. 用错误密码登录，确认提示“账号或密码错误”。
9. 访问 http://localhost:8080/swagger-ui.html，确认 Swagger 可打开。

- [ ] **Step 4: 更新 README 的“当前进度”**

将 `README.md` 中的：
```markdown
当前进度：P0 Week 1（工程骨架、认证、岗位模板）。
```
改为：
```markdown
当前进度：P0 Week 1 已完成（工程骨架、JWT 认证、岗位模板、登录注册页、岗位选择页）。

下一步：P0 Week 2（简历上传解析、创建面试、面试蓝图）。
```

- [ ] **Step 5: 提交**

Run:
```bash
cd /e/work
git add README.md
git commit -m "docs: mark P0 week 1 complete"
git status --short
```

Expected: `git status` 输出为空（工作区干净）。

---

## 自检结果

- **Spec coverage:** W1 的工程脚手架、数据库设计、登录注册、岗位模板均已覆盖；简历、面试、AI、SSE、RAG 明确留到 W2-W5。
- **Placeholder scan:** 计划中没有 TBD/TODO；所有代码步骤都有完整代码。
- **Type consistency:** `User`、`RefreshToken`、`JobTemplate`、`JwtService`、`RefreshTokenService`、`AuthService`、`TokenResponse` 的字段和方法在前后任务中保持一致；前端 `TokenResponse` 与后端 JSON 字段一致。

---

## 执行记录（2026-10-26）

- 分支：`feat/p0-week1`（基于 `main` 的初始提交 `2e6cacd`）。
- 提交：`156292e` → `ebb018e`，共 13 个提交。
- 后端测试：`mvn -f backend/pom.xml test` → **11 tests, 0 failures, 0 errors**。
- 前端构建：`npm --prefix frontend run build` → 成功。
- 运行时验证：`/actuator/health`、`/api/job-templates`、`/v3/api-docs`、注册/登录/刷新令牌轮换均通过。
- 浏览器验收（Playwright + Chrome）：注册 → 首页显示 4 张岗位卡片 → 退出登录 → 返回登录页，全部通过。
- 实施中的两处调整：
  - `mvn test` 配置 Surefire 同时匹配 `*Test.java` 与 `*IT.java`，确保集成测试默认执行。
  - CORS 同时允许 `http://localhost:5173` 与 `http://127.0.0.1:5173`，便于本地浏览器验收。
  - 前端构建脚本改为 `vue-tsc --noEmit && vite build`，避免 `vue-tsc -b` 在源码目录生成 `.js` / `.tsbuildinfo` 产物。
