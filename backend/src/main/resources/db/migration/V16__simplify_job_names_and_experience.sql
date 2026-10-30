-- 岗位名称收敛为一级方向；工作年限改到创建面试的测试选项中。
-- Java 后端校招/1-3 年合并为一个岗位，Spring Cloud 作为 Java 后端方向下的旧模板保留但不再展示。
UPDATE interview_track
SET name = 'Java 后端', level = '通用', status = 'ACTIVE', sort_order = 10
WHERE code = 'java-backend-campus';

UPDATE interview_track
SET name = 'Java 后端（旧 1-3 年）', level = '通用', status = 'INACTIVE', sort_order = 99
WHERE code = 'java-backend-junior';

UPDATE interview_track
SET name = 'Java 全栈', level = '通用', status = 'ACTIVE', sort_order = 20
WHERE code = 'java-fullstack-junior';

UPDATE interview_track
SET name = 'Java + AI', level = '通用', status = 'ACTIVE', sort_order = 30
WHERE code = 'java-ai';

UPDATE interview_track
SET name = 'Spring Cloud 微服务', level = '通用', status = 'INACTIVE', sort_order = 98
WHERE code = 'java-microservices';

UPDATE job_template
SET title = 'Java 后端', level = '通用', status = 'ACTIVE', sort_order = 10
WHERE title = 'Java 后端开发（校招）';

UPDATE job_template
SET level = '通用', status = 'INACTIVE', sort_order = 99
WHERE title LIKE 'Java 后端开发（1%';

UPDATE job_template
SET title = 'Java 全栈', level = '通用', status = 'ACTIVE', sort_order = 20
WHERE title LIKE 'Java 全栈开发%';

UPDATE job_template
SET title = 'Java + AI', level = '通用', status = 'ACTIVE', sort_order = 30
WHERE title = 'Java + AI 应用开发';

UPDATE job_template
SET level = '通用', status = 'INACTIVE', sort_order = 98
WHERE title = 'Spring Cloud 微服务开发';

-- 前端岗位先建 DRAFT 题库和隐藏模板，等 Java 题库完善后再补题并开放。
INSERT INTO interview_track
(code, name, role_family, primary_language, level, description,
 rubric_code, config_json, status, sort_order, created_at, updated_at)
VALUES
('frontend', '前端', 'FRONTEND', 'JS_TS', '通用',
 'JavaScript/TypeScript、Vue/React、CSS、浏览器、工程化',
 'FRONTEND',
 '{"focusTags":["JavaScript","TypeScript","Vue","React","CSS","浏览器","工程化"],"questionMix":{"basic":40,"project":25,"scenario":25,"ai":10}}',
 'DRAFT', 40, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT INTO job_template
(title, level, category, jd_text, config_json, status, sort_order, track_id, created_at, updated_at)
VALUES
('前端', '通用', '前端',
 '负责 Web 前端开发，使用 JavaScript/TypeScript 与 Vue/React，参与组件设计、接口联调、性能优化和工程化建设。',
 '{"basic":40,"project":25,"scenario":25,"ai":10}',
 'INACTIVE', 40,
 (SELECT id FROM interview_track WHERE code = 'frontend'),
 CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT INTO track_question (track_id, question_id, sort_order, weight, enabled, created_at, updated_at)
SELECT
    t.id,
    q.id,
    0,
    120,
    1,
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3)
FROM question_bank q
JOIN interview_track t ON t.code = 'frontend'
WHERE q.status = 'ACTIVE'
  AND q.deleted = 0
  AND q.category = 'Vue 和前端基础';
