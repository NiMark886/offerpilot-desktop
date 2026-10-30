ALTER TABLE interview_track ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'TECH';

ALTER TABLE job_template ADD COLUMN user_id BIGINT NULL;
ALTER TABLE job_template ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'SYSTEM';
CREATE INDEX idx_job_template_user_status ON job_template(user_id, status);

ALTER TABLE interview_session ADD COLUMN generation_mode VARCHAR(20) NOT NULL DEFAULT 'AI';

INSERT INTO interview_track
(code, name, category, role_family, primary_language, level, description,
 rubric_code, config_json, status, sort_order, created_at, updated_at)
VALUES
('sales', '销售岗位', 'GENERAL', 'SALES', 'NONE', '通用',
 '销售认知、客户开拓、需求挖掘、方案呈现、异议处理、谈判成交、过程复盘',
 'SALES',
 '{"retrievalMode":"BANK_AI","competencies":[{"name":"客户开拓","weight":20,"description":"寻找、筛选并触达潜在客户"},{"name":"需求挖掘","weight":20,"description":"识别客户痛点、预算、决策链和采购节奏"},{"name":"方案呈现","weight":15,"description":"把产品能力翻译成客户价值并完成演示"},{"name":"异议处理","weight":15,"description":"识别异议类型并推进客户认知"},{"name":"谈判成交","weight":15,"description":"交换条件、处理谈判分歧并推进签约"},{"name":"数据复盘","weight":15,"description":"用漏斗、转化率和丢单原因改进动作"}],"reportDimensions":{"customerInsight":"客户洞察","communication":"沟通说服","resilience":"抗压应变","resultOrientation":"结果导向","jobMatch":"岗位匹配"},"questionMix":{"behavioral":50,"scenario":30,"rolePlay":10,"motivation":10}}',
 'ACTIVE', 100, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));
