# CareCopilot

[English README](README.md)

CareCopilot 是一个面向中文医疗助手产品的 Java / Spring Boot 后端项目。它重点实现医疗 Agent 的编排、安全边界、可审计执行轨迹和可训练 telemetry，而不是优先做 UI。当前项目适合作为医疗 Agent 应用后端，以及与 AgentPlane 这类 Agent Infra 平台集成的参考实现。

> 医疗安全说明：CareCopilot 不是医疗器械，不能替代医生诊断、治疗或急救服务。本项目用于医疗助手工作流、安全路由和 Agent Infra 集成实践。

## 核心能力

- 创建病例，并把每个病例绑定到 AgentPlane session。
- 将用户输入先识别为通用对话或医疗相关需求。
- 只有识别到医疗相关需求后，才返回确认卡片契约。
- 支持三类医疗任务候选：症状分诊、报告解读、就诊准备。
- 支持多轮医疗问询上下文，并提供清空上下文接口。
- 内置红旗症状和用药变更安全边界检查。
- 记录病例时间线、审计事件、AgentPlane run、artifact 和 task job。
- 从脱敏文本与 Agent 执行 telemetry 导出可训练 JSONL 数据。
- 提供健康检查、Prometheus 指标、Docker Compose、Kubernetes 和 Helm 部署资产。

## 架构蓝图

```mermaid
flowchart TB
  subgraph Channels["产品入口"]
    App["CareCopilot App"]
    Console["AgentPlane 测试控制台"]
    OpenAPI["合作方 / Open API Client"]
  end

  subgraph Backend["CareCopilot 后端"]
    API["Spring Boot API<br/>病例、对话、工作流、数据集"]
    Context["病例与对话上下文<br/>槽位、待确认意图、记忆"]
    Safety["安全与确认 Guard<br/>红旗症状、用药边界、免责声明"]
    Orchestrator["Orchestrator Agent<br/>观察、规划、行动、追踪"]
    Router["意图与任务路由<br/>GENERAL_CHAT / MEDICAL_RELATED"]
    Card["医疗确认卡片<br/>症状分诊 / 报告解读 / 就诊准备"]
  end

  subgraph Agents["医疗 Agent 层"]
    General["通用对话 Agent"]
    Supervisor["医疗 Supervisor Agent"]
    Symptom["症状分诊 Agent"]
    Report["报告解读 Agent"]
    Visit["就诊准备 Agent"]
    Followup["随访与健康教育 Agent"]
  end

  subgraph Skills["医疗 Skill 层"]
    History["病史采集 Skill"]
    RedFlag["红旗分诊 Skill"]
    ReportParse["报告解析 Skill"]
    Medication["用药安全 Skill"]
    Guideline["指南检索 Skill"]
    Department["科室与就诊路径 Skill"]
    Summary["就诊摘要生成 Skill"]
  end

  subgraph MCP["MCP Tool Server 层"]
    KnowledgeMcp["医疗知识库 MCP"]
    CaseMcp["病例库 MCP"]
    ReportMcp["报告 / OCR MCP"]
    DrugMcp["药物相互作用 MCP"]
    HospitalMcp["医院 / 挂号 MCP"]
  end

  subgraph Data["医疗数据层"]
    Knowledge["医疗知识库<br/>指南、FAQ、循证材料、向量索引"]
    CaseStore["病例库<br/>档案、时间线、对话"]
    Audit["审计与 Trace 库<br/>tool call、决策、安全检查"]
    Dataset["训练数据构建<br/>脱敏文本 + Agent telemetry"]
    Policy["安全策略库<br/>红旗规则、免责声明、升级就医规则"]
  end

  subgraph Infra["Agent Infra 边界"]
    AgentPlane["AgentPlane Control Plane<br/>session、run、event、artifact、job"]
    Worker["AgentScope Java Worker<br/>Agent runtime 执行"]
    Model["模型网关<br/>本地或云端 LLM"]
  end

  App --> API
  Console --> API
  OpenAPI --> API
  API --> Context
  API --> Safety
  Context --> Orchestrator
  Safety --> Orchestrator
  Orchestrator --> Router
  Router --> General
  Router --> Card
  Card --> Supervisor
  Supervisor --> Symptom
  Supervisor --> Report
  Supervisor --> Visit
  Supervisor --> Followup
  Symptom --> History
  Symptom --> RedFlag
  Symptom --> Medication
  Report --> ReportParse
  Report --> Guideline
  Visit --> Department
  Visit --> Summary
  Visit --> Medication
  Followup --> Guideline
  History --> CaseMcp
  RedFlag --> KnowledgeMcp
  RedFlag --> Policy
  ReportParse --> ReportMcp
  Medication --> DrugMcp
  Medication --> Policy
  Guideline --> KnowledgeMcp
  Department --> HospitalMcp
  Summary --> CaseMcp
  KnowledgeMcp --> Knowledge
  CaseMcp --> CaseStore
  ReportMcp --> CaseStore
  DrugMcp --> Knowledge
  HospitalMcp --> Knowledge
  Orchestrator --> AgentPlane
  AgentPlane --> Worker
  Worker --> Model
  Context --> CaseStore
  Orchestrator --> Audit
  Audit --> Dataset
```

CareCopilot 是医疗领域 Agent 应用，AgentPlane 是底层 Agent Infra 控制平面，用于记录 session、run、event、artifact 和 worker job。当前后端已经落地病例库、对话上下文、医疗确认流程、三类医疗工作流、审计轨迹、训练数据导出和 AgentPlane 集成。医疗知识库、MCP Tool Server 和更完整的医疗 Skill 层是后续生产级架构面。

## 目录结构

```text
.
├── deploy/                  # Docker Compose、Kubernetes、Helm 部署资产
├── docs/                    # 部署与运维说明
├── src/main/java/dev/carecopilot
│   ├── agentplane/          # AgentPlane HTTP Client 与 DTO
│   ├── api/                 # REST Controller 与错误处理
│   ├── domain/              # 请求和响应模型
│   ├── service/             # Agent 编排与医疗工作流逻辑
│   └── store/               # 内存与 JDBC 状态存储
├── src/main/resources/      # Spring Boot 配置
└── src/test/java/dev/carecopilot
```

## 环境要求

- Java 17
- Maven 3.9+
- Docker 或 Kubernetes，用于生产化部署验证
- 如需完整 session/run/job 链路，需要启动 AgentPlane

## 运行测试

```bash
mvn -q test
```

## 本地运行

如需完整集成链路，先启动 AgentPlane 并暴露在 `http://127.0.0.1:18080`，然后运行：

```bash
mvn spring-boot:run
```

默认服务端口为 `18081`。

## 生产 Profile

```bash
SPRING_PROFILES_ACTIVE=prod \
CARECOPILOT_JDBC_URL=jdbc:postgresql://localhost:5432/carecopilot \
CARECOPILOT_JDBC_USERNAME=carecopilot \
CARECOPILOT_JDBC_PASSWORD=carecopilot \
CARECOPILOT_AGENTPLANE_BASE_URL=http://127.0.0.1:18080 \
java -jar target/carecopilot-backend-0.1.0-SNAPSHOT.jar
```

Docker Compose、Kubernetes、Helm 和数据集导出示例见 [docs/deployment.md](docs/deployment.md)。

## API

- `POST /cases`
- `GET /cases/{caseId}`
- `GET /cases/{caseId}/timeline`
- `GET /cases/{caseId}/audit-events`
- `POST /chat/intent`
- `POST /chat/confirm`
- `POST /chat/context/clear`
- `POST /workflows/symptom-intake`
- `POST /workflows/report-explanation`
- `POST /workflows/visit-preparation`
- `POST /datasets/export/jsonl`
- `GET /evals/carecopilot/mvp`
- `GET /actuator/health/readiness`
- `GET /actuator/prometheus`

## 开源协议

Apache License 2.0，见 [LICENSE](LICENSE)。
