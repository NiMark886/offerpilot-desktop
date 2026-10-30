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
