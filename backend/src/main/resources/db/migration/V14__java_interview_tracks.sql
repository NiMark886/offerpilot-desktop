CREATE TABLE interview_track (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    role_family VARCHAR(32) NOT NULL,
    primary_language VARCHAR(32) NOT NULL,
    level VARCHAR(32) NOT NULL,
    description VARCHAR(512) NULL,
    rubric_code VARCHAR(64) NOT NULL,
    config_json TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE UNIQUE INDEX uk_interview_track_code ON interview_track(code);
CREATE INDEX idx_interview_track_status_sort ON interview_track(status, sort_order);

CREATE TABLE track_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    track_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    weight INT NOT NULL DEFAULT 100,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE UNIQUE INDEX uk_track_question ON track_question(track_id, question_id);
CREATE INDEX idx_track_question_track ON track_question(track_id, enabled);
CREATE INDEX idx_track_question_question ON track_question(question_id);

ALTER TABLE job_template ADD COLUMN track_id BIGINT NULL;
CREATE INDEX idx_job_template_track ON job_template(track_id);

ALTER TABLE interview_session ADD COLUMN track_id BIGINT NULL;
CREATE INDEX idx_interview_session_track ON interview_session(track_id);

INSERT INTO interview_track
(code, name, role_family, primary_language, level, description,
 rubric_code, config_json, status, sort_order, created_at, updated_at)
VALUES
('java-backend-campus', 'Java 后端开发', 'BACKEND', 'JAVA', '校招',
 'Java 基础、集合、并发、JVM、Spring、MySQL、Redis',
 'BACKEND',
 '{"focusTags":["Java","Spring Boot","MySQL","Redis"],"questionMix":{"basic":35,"project":30,"scenario":20,"ai":15}}',
 'ACTIVE', 10, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('java-backend-junior', 'Java 后端开发', 'BACKEND', 'JAVA', '1-3 年',
 'Java 后端进阶、框架、数据库、缓存、项目与场景',
 'BACKEND',
 '{"focusTags":["Java","Spring Boot","MySQL","Redis","并发","JVM"],"questionMix":{"basic":30,"project":35,"scenario":20,"ai":15}}',
 'ACTIVE', 20, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('java-fullstack-junior', 'Java 全栈开发', 'FULLSTACK', 'JAVA', '1-3 年',
 'Java 后端为主，Vue/React 前端为辅，含接口联调与部署',
 'FULLSTACK',
 '{"focusTags":["Java","Spring Boot","Vue","React","MySQL","接口联调"],"questionMix":{"basic":30,"project":30,"scenario":25,"ai":15},"questionSourceMix":{"backend":60,"frontend":30,"fullstack":10}}',
 'ACTIVE', 25, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('java-ai', 'Java + AI 应用开发', 'AI_APP', 'JAVA', '1-3 年',
 'Java 后端 + LLM/RAG/Agent/Spring AI',
 'AI_APP',
 '{"focusTags":["Java","Spring AI","RAG","Agent","Embedding"],"questionMix":{"basic":25,"project":30,"scenario":25,"ai":20}}',
 'ACTIVE', 30, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('java-microservices', 'Spring Cloud 微服务开发', 'BACKEND', 'JAVA', '1-3 年',
 'Spring Cloud Alibaba、Nacos、OpenFeign、Gateway、分布式治理',
 'BACKEND',
 '{"focusTags":["Spring Cloud","Nacos","OpenFeign","Gateway","分布式"],"questionMix":{"basic":20,"project":35,"scenario":30,"ai":15}}',
 'ACTIVE', 40, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

UPDATE job_template
SET track_id = (SELECT id FROM interview_track WHERE code = 'java-backend-campus')
WHERE title = 'Java 后端开发（校招）';

UPDATE job_template
SET track_id = (SELECT id FROM interview_track WHERE code = 'java-backend-junior')
WHERE title = 'Java 后端开发（1–3 年）';

UPDATE job_template
SET track_id = (SELECT id FROM interview_track WHERE code = 'java-ai')
WHERE title = 'Java + AI 应用开发';

UPDATE job_template
SET track_id = (SELECT id FROM interview_track WHERE code = 'java-microservices')
WHERE title = 'Spring Cloud 微服务开发';

INSERT INTO job_template
(title, level, category, jd_text, config_json, status, sort_order, track_id, created_at, updated_at)
VALUES
('Java 全栈开发（1–3 年）', '1-3 年', 'Java 全栈',
 '负责前后端一体化业务开发，后端使用 Java/Spring Boot，前端使用 Vue/React，参与接口设计、联调、部署与性能优化。要求能讲清前后端协作、鉴权、跨域、数据库设计和线上排障。',
 '{"basic":30,"project":30,"scenario":25,"ai":15}',
 'ACTIVE', 25,
 (SELECT id FROM interview_track WHERE code = 'java-fullstack-junior'),
 CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT INTO track_question (track_id, question_id, sort_order, weight, enabled, created_at, updated_at)
SELECT
    t.id,
    q.id,
    0,
    CASE WHEN q.category IN ('Java 基础', 'Java 集合', 'Java 并发', 'JVM') THEN 120 ELSE 100 END,
    1,
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3)
FROM question_bank q
JOIN interview_track t ON t.code = 'java-backend-campus'
WHERE q.status = 'ACTIVE'
  AND q.deleted = 0
  AND q.category <> 'Vue 和前端基础';

INSERT INTO track_question (track_id, question_id, sort_order, weight, enabled, created_at, updated_at)
SELECT
    t.id,
    q.id,
    0,
    CASE WHEN q.category IN (
        'Spring Boot 和 Spring', 'MySQL 和 MyBatis', 'Redis 和缓存',
        'JWT 和登录认证', '项目经历追问', '实习经历追问'
    ) THEN 120 ELSE 100 END,
    1,
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3)
FROM question_bank q
JOIN interview_track t ON t.code = 'java-backend-junior'
WHERE q.status = 'ACTIVE'
  AND q.deleted = 0
  AND q.category <> 'Vue 和前端基础';

INSERT INTO track_question (track_id, question_id, sort_order, weight, enabled, created_at, updated_at)
SELECT
    t.id,
    q.id,
    0,
    CASE WHEN q.category IN ('Vue 和前端基础', '项目经历追问', '开放题') THEN 120 ELSE 100 END,
    1,
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3)
FROM question_bank q
JOIN interview_track t ON t.code = 'java-fullstack-junior'
WHERE q.status = 'ACTIVE'
  AND q.deleted = 0;

INSERT INTO track_question (track_id, question_id, sort_order, weight, enabled, created_at, updated_at)
SELECT
    t.id,
    q.id,
    0,
    CASE WHEN q.category IN (
        'Spring Boot 和 Spring', 'MySQL 和 MyBatis', 'Redis 和缓存',
        '项目经历追问', '开放题'
    ) THEN 120 ELSE 100 END,
    1,
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3)
FROM question_bank q
JOIN interview_track t ON t.code = 'java-ai'
WHERE q.status = 'ACTIVE'
  AND q.deleted = 0
  AND q.category <> 'Vue 和前端基础';

INSERT INTO track_question (track_id, question_id, sort_order, weight, enabled, created_at, updated_at)
SELECT
    t.id,
    q.id,
    0,
    CASE WHEN q.category IN (
        'Spring Boot 和 Spring', 'MySQL 和 MyBatis', 'Redis 和缓存',
        'JWT 和登录认证', '项目经历追问', '开放题'
    ) THEN 120 ELSE 100 END,
    1,
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3)
FROM question_bank q
JOIN interview_track t ON t.code = 'java-microservices'
WHERE q.status = 'ACTIVE'
  AND q.deleted = 0
  AND q.category <> 'Vue 和前端基础';
