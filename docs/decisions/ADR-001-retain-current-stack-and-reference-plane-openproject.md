# ADR-001：保留现有技术栈，参考 Plane 交互与 OpenProject 模型

## 状态

Accepted

## 日期

2026-09-20

## 背景

项目已基于 Vue 3、TypeScript、Element Plus、Spring Boot、MyBatis-Flex 和 PostgreSQL 建立可运行基础，已完成认证权限、项目、项目成员、文件元数据及分片上传等能力。项目后续需要更成熟的项目管理交互和领域模型。

Plane 提供现代化工作区、项目导航、多视图和详情编辑交互；OpenProject 的 Work Package、项目成员角色、工作流、关系和历史模型适合作为项目管理语义参考。两者的实现技术栈和许可证均与当前项目不同。

## 决策

1. 保留现有 Vue 3 + Spring Boot + PostgreSQL 架构，现有应用继续作为唯一业务和数据底座。
2. 参考 Plane 的信息架构和交互模式，使用现有前端技术栈自主实现，不直接复制 Plane 代码、图标、样式或品牌资源。
3. 参考 OpenProject Work Package 设计统一“工作项”，逐步承载任务、需求、Bug、客户建议和里程碑。
4. 部署资产、服务器凭据、环境指纹、部署方案、风险决策和日报周报等专业业务继续使用独立模型，只通过显式关联连接工作项。
5. 保持应用代码 Apache-2.0 目标。引入任何 GPL/AGPL 代码前，必须单独进行许可证兼容性和源码提供义务评估。
6. 第一期按已确认顺序完成资料与部署复用链路，只渐进调整交互壳层；统一工作项从第二期开始落地。

## 交互与模型边界

### 可参考的 Plane 交互

- 全局工作区与项目级侧边栏。
- 列表、看板、时间线等多视图入口。
- 列表选择与右侧详情面板联动。
- 统一搜索、快速创建、空状态和加载/错误反馈。

### 可参考的 OpenProject 模型

- 工作项类型、状态和可配置工作流。
- 项目成员、项目角色与操作权限。
- 父子、前置、阻塞和其他显式关系。
- 可追溯的状态变更和审计记录。

## 替代方案

### 直接 Fork Plane

- 优点：现代 UI 和敏捷项目管理能力起点高。
- 缺点：需要将 Vue/Spring Boot 迁移到 React/Django 方向，且内部实施、部署复用和凭据安全仍需大量定制。
- 未选原因：会放弃已完成投入，迁移风险高。

### 直接 Fork OpenProject

- 优点：项目管理领域能力成熟。
- 缺点：Ruby on Rails + Angular 与当前技术栈差异大，定制专业业务和长期跟随上游升级成本高。
- 未选原因：超出当前团队的简单性和可维护性目标。

### 独立部署 OpenProject 并通过 API 集成

- 优点：可快速获得成熟的通用项目管理能力。
- 缺点：形成双系统，项目、成员、权限、文件和登录数据需要同步。
- 未选原因：运维与数据一致性成本高于直接在现有应用中实现必要能力。

## 后果

- 保留已完成功能和团队熟悉的技术栈，降低迁移风险。
- UI 改造和工作项模型需要自主实现，短期开发量高于直接部署成品。
- 交互必须逐模块迁移，新旧页面在过渡期需保持一致导航和设计变量。
- 工作项建表前需要单独确认类型、状态流、关系和权限契约，不直接照搬 OpenProject 全部复杂性。
- 未来若改为直接使用或合并 GPL/AGPL 代码，必须新建 ADR 替代本决策。

## 参考

- Plane：<https://github.com/makeplane/plane>
- Plane 自托管版本与许可说明：<https://developers.plane.so/self-hosting/editions-and-versions>
- OpenProject Work Packages：<https://www.openproject.org/docs/user-guide/work-packages/>
- OpenProject 应用架构：<https://www.openproject.org/docs/development/application-architecture/>
