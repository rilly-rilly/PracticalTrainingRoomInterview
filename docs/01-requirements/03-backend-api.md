# 03 后端分层与接口契约设计

> 适用范围：学生实训室进组选拔系统（Vue 3 + Java 17 / Spring Boot 3 + MyBatis + MySQL 8）。
> 事实基线：前端演示版 `frontend/src/mock/server.js`、`frontend/src/mock/seed.js`、`frontend/src/api/index.js`、`frontend/src/router/index.js`。
> 本文定义真实后端的分层结构、统一响应、REST 契约、登录态与越权防护方案。接口路径、字段、状态值必须与演示版保持一致，演示版没有的接口不得擅自新增（`/api/public/demo-accounts`、`/api/public/reset` 仅作为演示期旁路保留）。

---

## 1. 技术栈、包结构与三层职责

### 1.1 技术栈与依赖边界

| 项目 | 选型 | 说明 |
| --- | --- | --- |
| JDK | Java 17 | 使用 record、sealed class 等语法，LTS |
| Web | Spring Boot 3.x / Spring MVC | REST + 拦截器 |
| 持久层 | MyBatis 3 | Mapper 接口 + XML，SQL 可审计 |
| 数据库 | MySQL 8 | InnoDB，事务与行锁 |
| 校验 | Jakarta Bean Validation | `@Validated` + `@Valid` |
| 密码 | BCrypt | 不落明文，演示版哈希仅用于前端 mock |
| 登录态 | 数据库 token 表 | 课程作业不引入 Redis，满足“登出立即失效” |

> 不引入 Spring Security / JWT / Redis。登录态采用「随机不可猜 token + 服务端会话表」，由拦截器统一校验角色。这样既符合课程作业边界，也能实现真实的后端鉴权。

### 1.2 包结构（com.example.trainee）

```text
com.example.trainee
├── TraineeApplication.java             // 启动类
├── common: ApiResponse, PageResult, ErrorCode, BusinessException, GlobalExceptionHandler, LoginUser, AuthInterceptor, CurrentUserArgumentResolver, WebMvcConfig
├── config: MybatisConfig, JacksonConfig, PasswordConfig
├── controller: AuthController, PublicController, RoomController, StudentController, TeacherController, AdminImportController, AdminScheduleController, AdminNoticeController, AdminAdmissionController, AdminAdjustmentController, AdminPublicityController
├── service: AuthService, PublicService, RoomService, IntentionService, StudentQueryService, TeacherService, ImportService, ScheduleService, NoticeService, AdmissionService, AdjustmentService, PublicityService, OperationLogService, state/IntentionStatusMachine
├── mapper: AdminMapper, TeacherMapper, StudentMapper, RoomMapper, IntentionMapper, InterviewMapper, ConfirmationMapper, AdjustmentMapper, PublicityMapper, ScheduleMapper, NoticeMapper, AuthTokenMapper, ImportBatchMapper, OperationLogMapper
├── entity: Admin, Teacher, Student, Room, Intention, Interview, Confirmation, Adjustment, Publicity, Schedule, Notice, AuthToken, ImportBatch, OperationLog
├── dto/request: LoginRequest, SubmitIntentionRequest, ConfirmRequest, InterviewRequest, DecisionRequest, ImportRequest, ScheduleUpdateRequest, NoticeCreateRequest, QuotaUpdateRequest, ExpireRequest, AdjustmentRunRequest
├── dto/response: LoginResponse, ProfileResponse, HomeResponse, RoomResponse, IntentionResponse, StudentStatusResponse, TeacherApplicantResponse, ImportResultResponse, AdmissionOverviewResponse, AdjustmentOverviewResponse, PublicityResponse
└── resources: application.yml, mapper/*.xml
```

### 1.3 三层职责与红线

| 层 | 只负责 | 明确禁止 |
| --- | --- | --- |
| `controller` | 接收 HTTP 参数、`@Valid` 校验、调用 Service、包装 `ApiResponse`、返回 HTTP 状态码 | 不写业务判断、不直接调 Mapper、不修改状态字段、不计算名额 |
| `service` | 业务规则、状态机、权限归属校验、事务边界、日志编排、DTO 转换 | 不拼 SQL、不依赖 `HttpServletRequest`（当前用户以 `LoginUser` 传入） |
| `mapper` | SQL 执行与结果映射（XML 为主） | 不写 if/else 业务分支、不抛业务码、不跨表编排事务 |
| `entity` | 数据库行映射 | 不直接作为接口出参（避免暴露密码哈希、内部字段） |
| `dto` | 请求/响应契约，含校验注解 | 不承载持久化逻辑 |
| `common` | 统一响应、异常、登录主体、拦截器 | 不包含具体业务 |
| `config` | 框架配置、拦截器注册、密码器、MyBatis | 不写业务逻辑 |

**核心纪律：业务规则只写在 Service 层；Controller 只做参数接收与响应包装；Mapper 只做 SQL。**

### 1.4 请求调用链

```text
HTTP Request
   │
   ▼
AuthInterceptor（解析 Bearer token → 校验角色 → 注入 LoginUser）
   │
   ▼
Controller（@Valid 参数校验；取 LoginUser；调用 Service；包装 ApiResponse）
   │
   ▼
Service（业务校验 → 状态机 → 事务 → 调 Mapper → 写 operation_log）
   │
   ▼
Mapper（SQL / FOR UPDATE / 唯一索引）
   │
   ▼
MySQL
```

---

## 2. 统一响应结构

### 2.1 结构定义

所有接口统一返回：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

- `code`：采用 HTTP 语义状态码，同时作为业务码；HTTP 响应状态与 `body.code` 保持一致。
- `message`：面向用户的中文提示，前端可直接 toast。
- `data`：成功时为业务数据；失败时可为 `null`，校验类失败可携带结构化明细。

```java
public record ApiResponse<T>(int code, String message, T data) {
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(200, message, data);
    }
    public static <T> ApiResponse<T> fail(int code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }
}
```

### 2.2 成功与失败示例

成功示例（`POST /api/student/intentions`）：

```json
{
  "code": 200,
  "message": "意向提交成功",
  "data": [
    {
      "id": "int-16",
      "teacherId": "tch-1",
      "teacherName": "李伯谦",
      "roomId": "room-1",
      "roomName": "智能视觉实训室",
      "priority": 1,
      "status": "待面试",
      "round": 1,
      "rank": 0,
      "submittedAt": 1780000000000
    }
  ]
}
```

失败示例（名额已满，`409`）：

```json
{
  "code": 409,
  "message": "名额已满：智能视觉实训室 招收上限 4 人，当前已占用 4 人（409）",
  "data": {
    "roomId": "room-1",
    "quota": 4,
    "used": 4
  }
}
```

失败示例（导入校验失败，`422`，`data.failures` 带行号）：

```json
{
  "code": 422,
  "message": "严格模式：共 2 行校验失败，本次导入整体回滚，未写入任何数据",
  "data": {
    "imported": 0,
    "skipped": 2,
    "strict": true,
    "accounts": [],
    "failures": [
      { "line": 2, "no": "S2026010", "name": "张三", "reasons": ["学号缺失"] },
      { "line": 4, "no": "T2002", "name": "李四", "reasons": ["账号已存在：T2002"] }
    ]
  }
}
```

### 2.3 失败码区分表

| code | 语义 | 典型触发 | `data` 建议 |
| --- | --- | --- | --- |
| 200 | 成功 | 查询、提交、确认成功 | 业务数据 |
| 400 | 参数错误 | 缺少账号密码、非法枚举、导师不存在、时间格式错误 | 可选字段定位 |
| 401 | 未登录 / 登录态失效 | 无 token、token 已删除、token 过期、密码错误 | `null` |
| 403 | 无权限 / 越权 | 学生访问教师接口、教师访问管理员接口、操作他人意向 | `null` |
| 404 | 资源不存在 | 实训室、意向、公告、日程不存在 | `null` |
| 409 | 名额已满 | 导师拟录取时实训室已满；名额上限低于已占用 | `{roomId, quota, used}` |
| 410 | 时间窗口关闭 | 意向填报/确认窗口已关闭、窗口未结束不能逾期处理 | `{window}` 可选 |
| 422 | 状态不允许或校验失败 | 非待面试录分、非面试完成决策、重复填报、简介太短、导入校验失败 | 导入时带 `failures` |
| 500 | 服务异常 | 未捕获异常、数据库异常 | `null`，生产环境不暴露堆栈 |

### 2.4 前端按 code 分流

前端 `frontend/src/api/http.js` 已按以下方式处理：

| code | 前端行为 |
| --- | --- |
| 200 | `res.data` 直接返回，业务组件读 `data` |
| 401 | 清除 `localStorage` 登录态，跳转登录页，toast 红色提示 |
| 403 | toast 红色提示，路由守卫/页面可跳 `/403` |
| 404 | toast 警告，列表页刷新或返回上一页 |
| 409 / 410 / 422 | toast 警告并展示 `message`；422 导入页额外渲染 `data.failures` 行号表；409 可展示 `quota/used` |
| 400 / 500 | toast 警告或错误，400 保留表单内容供修正，500 提示稍后重试 |

约定：**前端不自行推断业务成功**，只以 `code === 200` 为成功；所有失败提示优先使用后端 `message`，不要在前端重复维护文案。

---

## 3. 完整 REST 接口清单

- 角色：`公开` 表示无需登录；`学生/导师/管理员` 表示必须携带有效 token。
- 所有需要登录的接口从 `Authorization: Bearer <token>` 解析当前用户，**不接受前端传入的 studentId / teacherId 作为身份依据**。
- 列表接口的出参保持演示版字段命名（前端已按此渲染）。

### 3.1 认证

| 模块 | 方法 | 路径 | 入参 | 出参要点 | 所需角色 |
| --- | --- | --- | --- | --- | --- |
| 认证 | POST | `/api/auth/login` | JSON：`username`、`password` | `data.token`、`data.role`（student/teacher/admin）、`data.profile` | 公开 |
| 认证 | POST | `/api/auth/logout` | 无 body，读取 Bearer token | `data=null`，旧 token 立即失效 | 学生/导师/管理员 |
| 认证 | GET | `/api/auth/me` | 无 | `data.role`、`data.profile` | 学生/导师/管理员 |

`login` 账号识别规则与演示版一致：

| 输入 | 角色 | 匹配字段 |
| --- | --- | --- |
| `admin` | 管理员 | 管理员固定账号 |
| 以 `T/t` 开头 | 导师 | `teacher.teacher_no` 忽略大小写 |
| 其他 | 学生 | `student.student_no` 忽略大小写 |

`profile` 字段：

| 角色 | profile |
| --- | --- |
| 管理员 | `id, role, name, username` |
| 导师 | `id, role, name, no, title, roomId, roomName` |
| 学生 | `id, role, name, no, major, grade` |

### 3.2 公共

| 模块 | 方法 | 路径 | 入参 | 出参要点 | 所需角色 |
| --- | --- | --- | --- | --- | --- |
| 公共 | GET | `/api/public/home` | 无 | `steps`（7 步流程，含 `state=未开始/进行中/已结束`）、`notices`、`rooms`（含 `remaining`）、`intentWindow` | 公开 |
| 公共 | GET | `/api/rooms` | 无 | 实训室列表：`id/code/name/direction/quota/remaining/teacherCount/tags` | 公开 |
| 公共 | GET | `/api/rooms/{id}` | path `id` | 实训室详情 + `remaining` + `teachers[]`；不存在 404 | 公开 |
| 演示旁路 | GET | `/api/public/demo-accounts` | 无 | 演示账号清单 | 公开（仅演示环境） |
| 演示旁路 | POST | `/api/public/reset` | 无 | 重置演示数据 | 公开（仅演示环境） |

> 后两个接口是前端演示版专用。真实后端可通过 `application.yml` 的 `demo.enabled=false` 关闭，或仅允许 `admin` 调用；正式验收文档不将其计入业务接口。

### 3.3 学生

| 模块 | 方法 | 路径 | 入参 | 出参要点 | 所需角色 |
| --- | --- | --- | --- | --- | --- |
| 学生 | GET | `/api/student/intentions` | 无 | 当前学生全部意向（含教师/实训室快照、`status`、`priority`、`round`、`rank`、`interview`） | 学生 |
| 学生 | POST | `/api/student/intentions` | JSON：`choices`（1~3 个 teacherId）、`intro`、`acceptInnerAdjust`、`acceptGlobalAdjust` | 提交后的意向列表 | 学生 |
| 学生 | GET | `/api/student/status` | 无 | `intentions`、`summary`（`admits/needChoose/autoConfirmed/confirmedIntentionId/waitlistRank/hasWaitlist/...`）、`confirmWindow`、`confirmations`、`adjustments` | 学生 |
| 学生 | POST | `/api/student/confirm` | JSON：`intentionId` | `data.confirmedIntentionId`、`data.givenUp[]` | 学生 |
| 学生 | GET | `/api/student/adjustments` | 无 | `records`、`summary`（`totalIntentions/rejected/acceptInnerAdjust/acceptGlobalAdjust`） | 学生 |
| 学生 | GET | `/api/student/final` | 无 | `stage`、`finalStatus`、`intention`、`publicity`、`intentions`、`adjustments` | 学生 |

`POST /api/student/intentions` 请求体：

```json
{
  "choices": ["tch-1", "tch-4", "tch-3"],
  "intro": "我是计算机科学与技术专业 2024 级学生，熟悉 Java 与 MySQL，希望进入实训室参与真实项目。",
  "acceptInnerAdjust": true,
  "acceptGlobalAdjust": true
}
```

字段约束：`choices` 至少 1 个、最多 3 个、不可重复；`intro` 去空格后不少于 20 字；`acceptInnerAdjust` / `acceptGlobalAdjust` 转布尔保存。提交成功后全部意向共用同一简介与调剂意愿，`priority` 按 `choices` 顺序为 1、2、3。

### 3.4 导师

| 模块 | 方法 | 路径 | 入参 | 出参要点 | 所需角色 |
| --- | --- | --- | --- | --- | --- |
| 导师 | GET | `/api/teacher/applicants` | query 可选：`status`（中文状态值） | `applications`、`teacher`、`room`（`used/remaining`）、`statusCounts` | 导师 |
| 导师 | GET | `/api/teacher/applicants/{id}` | path `id`（intentionId） | 单条意向详情；非本人报考 403 | 导师 |
| 导师 | POST | `/api/teacher/interviews` | JSON：`intentionId`、`attitude`、`logic`、`coding`、`commitment`、`comment` | `data.total`；意向状态变为「面试完成」 | 导师 |
| 导师 | POST | `/api/teacher/decision` | JSON：`intentionId`、`decision`（`admit`/`waitlist`/`reject`） | admit：`{status,autoConfirmed}`；waitlist：`{status,rank}`；reject：`{status}` | 导师 |

`POST /api/teacher/interviews` 请求体：

```json
{
  "intentionId": "int-8",
  "attitude": 9,
  "logic": 8,
  "coding": 9,
  "commitment": 8,
  "comment": "基础扎实，项目描述清楚。"
}
```

四个评分项均为 1~10 的整数，`total` 由服务端求和，前端传入的 `total` 不采信。录分成功后写入 `interview` 并更新状态；同一意向再次录分返回 422。

`POST /api/teacher/decision` 请求体：

```json
{ "intentionId": "int-10", "decision": "admit" }
```

`decision` 只接受三个枚举值。拟录取时服务端必须重新统计名额，前端展示的 `remaining` 不能作为录取依据。

### 3.5 管理员

| 模块 | 方法 | 路径 | 入参 | 出参要点 | 所需角色 |
| --- | --- | --- | --- | --- | --- |
| 管理员 | POST | `/api/admin/import` | JSON：`type`（`student/teacher`）、`strict`、`rows[]` | `imported`、`skipped`、`failures[]`（含行号）、`accounts[]`（一次性明文密码）、`batchId` | 管理员 |
| 管理员 | GET | `/api/admin/schedules` | 无 | 7 步日程，按 `step` 升序 | 管理员 |
| 管理员 | PUT | `/api/admin/schedules/{id}` | JSON：`title?`、`description?`、`startAt`、`endAt`（毫秒时间戳） | 更新后的日程 | 管理员 |
| 管理员 | GET | `/api/admin/notices` | 无 | 公告列表，按发布时间倒序 | 管理员 |
| 管理员 | POST | `/api/admin/notices` | JSON：`title`、`content`、`level`（普通/提醒/重要） | 新公告 | 管理员 |
| 管理员 | DELETE | `/api/admin/notices/{id}` | path `id` | `data=null`；不存在 404 | 管理员 |
| 管理员 | GET | `/api/admin/admissions` | 无 | `rooms[]`（quota/used/admitted/confirmed/waitlist/enrolled/intentions）、`teachers[]`、`intentions[]`、`stats`、`confirmWindow`、`logs` | 管理员 |
| 管理员 | PUT | `/api/admin/rooms/{id}/quota` | JSON：`quota`（1~20 整数） | `{id,quota}`；低于已占用 409 | 管理员 |
| 管理员 | POST | `/api/admin/admissions/expire` | JSON：`force`（布尔，演示强制模式） | `{expired,promoted,students}` | 管理员 |
| 管理员 | GET | `/api/admin/adjustments` | 无 | `students[]`（含调剂意愿与状态）、`records[]`、`rooms[]` | 管理员 |
| 管理员 | POST | `/api/admin/adjustments/run` | JSON：`type`（`inner/global/interview/supplement`） | 按类型返回 `{affected,skipped}` 或 `{affected}` | 管理员 |
| 管理员 | GET | `/api/admin/publicity` | 无 | `eligible[]`、`records[]`、`window` | 管理员 |
| 管理员 | POST | `/api/admin/publicity/publish` | 无 body | `data.created` | 管理员 |
| 管理员 | POST | `/api/admin/publicity/finalize` | JSON：`force`（布尔） | `data.enrolled` | 管理员 |
| 管理员 | GET | `/api/admin/logs` | 无 | 最近 40 条操作日志，倒序 | 管理员 |

`POST /api/admin/import` 请求体：

```json
{
  "type": "student",
  "strict": true,
  "rows": [
    { "姓名": "张三", "学号": "S2026010", "专业": "软件工程", "年级": "2024 级" },
    { "姓名": "李四", "学号": "S2026011", "专业": "计算机科学与技术", "年级": "2024 级" }
  ]
}
```

导师导入行字段：`姓名`、`工号`、`职称`（可选，缺省「讲师」）、`实训室编号`（必须存在于 `room.code`）。学生导入行字段：`姓名`、`学号`、`专业`、`年级`。`strict=true` 时任一行校验失败则整体 422 且不写库；`strict=false` 时跳过失败行，仅写入有效行。

其余管理员请求体：

```json
{ "title": "面试安排说明", "content": "请按导师通知时间参加面试。", "level": "提醒" }
```

```json
{ "title": "拟录取确认", "description": "请学生自主选择唯一去向。", "startAt": 1780000000000, "endAt": 1780086400000 }
```

```json
{ "quota": 5 }
```

```json
{ "force": false }
```

```json
{ "type": "inner" }
```

`adjustments/run` 的 `type` 语义：

| type | 含义 | 前置条件 |
| --- | --- | --- |
| `inner` | 实训室内部调剂（第一优先级） | 学生全部未录取，且至少一个意向接受内部调剂，未执行过同类型调剂 |
| `global` | 全院跨实训室调剂（第二优先级） | 学生全部未录取，且至少一个意向接受全院调剂，未执行过同类型调剂 |
| `interview` | 约谈（第三优先级） | 学生全部未录取，且所有意向均不服从调剂 |
| `supplement` | 分轮补录 | 存在「递补」记录且对应实训室有空余名额 |

---

## 4. 登录态方案

### 4.1 方案选择

不使用 JWT，不使用 Redis。登录成功后生成随机 token，落库到 `auth_token` 表；每个受保护请求都查表校验。登出直接删除该行，旧 token 立即失效，满足课程作业“可解释、可演示”的要求。

```sql
CREATE TABLE auth_token (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    token_hash  CHAR(64)     NOT NULL COMMENT 'SHA-256(token)，不存明文',
    role        VARCHAR(16)  NOT NULL COMMENT 'student/teacher/admin',
    user_id     BIGINT       NOT NULL COMMENT '业务用户主键',
    created_at  DATETIME     NOT NULL,
    expires_at  DATETIME     NOT NULL,
    UNIQUE KEY uk_token_hash (token_hash),
    KEY idx_user (role, user_id)
) ENGINE=InnoDB COMMENT='登录会话表';
```

- token 明文只返回给前端一次：`SecureRandom` 生成 32 字节随机数，Base64URL/Hex 编码，例如 `tok-student-1-xxxxxxxx` 仅作可读前缀，随机部分必须不可猜。
- 服务端存 `SHA-256(token)`；查询时对请求 token 做同样哈希。即使数据库泄露也无法直接复用 token。
- 有效期建议 12 小时；过期后拦截器返回 401。
- 登出 `DELETE FROM auth_token WHERE token_hash=?`，不保留“已登出但仍可用”的窗口。

### 4.2 前端存储

沿用 `frontend/src/auth.js` 的既有约定：

```js
localStorage.setItem('trainee-selection-auth-v1', JSON.stringify({
  token, role, profile
}))
```

- 页面刷新时 `restoreAuth()` 从 `localStorage` 恢复，token 不丢。
- Axios 请求拦截器自动加 `Authorization: Bearer <token>`。
- 401 时调用 `clearAuth()` 清除本地登录态并跳登录页。
- 前端路由守卫只做“体验层”防护（无 token 跳登录、角色不匹配跳 `/403`），**真正的权限以后端拦截器为准**。

### 4.3 后端拦截器与角色矩阵

`WebMvcConfig` 注册：

```java
registry.addInterceptor(authInterceptor)
        .addPathPatterns("/api/**")
        .excludePathPatterns(
            "/api/auth/login",
            "/api/public/**",
            "/api/rooms/**"
        );
```

拦截器伪代码：

```java
public class AuthInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        String path = req.getRequestURI();
        String token = extractBearer(req); // Authorization: Bearer xxx
        if (token == null || token.isBlank()) {
            throw new BusinessException(401, "登录态无效，请重新登录");
        }
        AuthToken session = authTokenMapper.selectByHash(sha256(token));
        if (session == null || session.getExpiresAt().isBefore(now())) {
            throw new BusinessException(401, "登录态无效，请重新登录");
        }
        LoginUser user = loadProfile(session); // role + userId + name
        req.setAttribute("loginUser", user);

        if (path.startsWith("/api/student/") && !"student".equals(user.role())) {
            throw new BusinessException(403, "当前角色无权访问该接口，已由后端拦截");
        }
        if (path.startsWith("/api/teacher/") && !"teacher".equals(user.role())) {
            throw new BusinessException(403, "当前角色无权访问该接口，已由后端拦截");
        }
        if (path.startsWith("/api/admin/") && !"admin".equals(user.role())) {
            throw new BusinessException(403, "当前角色无权访问该接口，已由后端拦截");
        }
        return true;
    }
}
```

角色矩阵：

| 路径前缀 | 允许角色 | 说明 |
| --- | --- | --- |
| `/api/auth/login` | 公开 | 登录 |
| `/api/public/**` | 公开 | 首页、演示旁路 |
| `/api/rooms/**` | 公开 | 实训室浏览 |
| `/api/auth/logout`、`/api/auth/me` | 学生/导师/管理员 | 任意已登录角色 |
| `/api/student/**` | 学生 | 导师、管理员访问返回 403 |
| `/api/teacher/**` | 导师 | 学生、管理员访问返回 403 |
| `/api/admin/**` | 管理员 | 学生、导师访问返回 403 |

> 管理员是否可以访问学生/教师接口由业务决定；按演示版路由，管理员调用 `/api/student/**`、`/api/teacher/**` 仍返回 403。实现时以角色矩阵为准，不能因为“管理员权限大”而放开全部路径。

### 4.4 登录与登出时序

```text
登录：POST /api/auth/login
  → 按用户名识别角色 → 查库 → BCrypt 校验
  → 生成 token → 存 auth_token(hash,role,userId,expires)
  → 返回 { token, role, profile }
  → 前端写 localStorage

登出：POST /api/auth/logout
  → 拦截器解析 token → DELETE auth_token WHERE token_hash=?
  → 返回 200
  → 前端 clearAuth()
  → 旧 token 再请求任何受保护接口 → 401
```

---

## 5. 越权防护（IDOR）

### 5.1 核心原则

1. **身份只来自 token**：学生相关接口一律用 `LoginUser.userId()` 查询，请求体不接收 `studentId`；导师接口同理不接收 `teacherId`。
2. **资源归属必须校验**：按 `intentionId` 操作前，先查意向，再校验 `intention.student_id == 当前学生` 或 `intention.teacher_id == 当前导师`。
3. **SQL 层再兜底**：更新语句尽量带归属条件，例如 `WHERE id=? AND student_id=?`，避免“先查后改”之间被并发篡改。
4. **管理员接口统一角色拦截**：`/api/admin/**` 只允许 admin，不依赖前端隐藏菜单。
5. **越权返回 403，不返回 404 掩盖**：与演示版提示一致，便于教学演示；真实生产也可统一返回 404 以避免资源探测，但本项目按演示版返回 403。

### 5.2 归属校验伪代码

```java
// 学生确认拟录取
@Transactional(rollbackFor = Exception.class)
public ConfirmResult confirm(String intentionId, LoginUser user) {
    Intention it = intentionMapper.selectById(intentionId);
    if (it == null) {
        throw new BusinessException(404, "意向记录不存在");
    }
    if (!it.getStudentId().equals(user.id())) {
        throw new BusinessException(403, "无权操作他人的意向记录");
    }
    // 后续状态校验、窗口校验、更新
}

// 导师查看报考学生
public IntentionResponse applicantDetail(String intentionId, LoginUser user) {
    Intention it = intentionMapper.selectById(intentionId);
    if (it == null) throw new BusinessException(404, "意向记录不存在");
    if (!it.getTeacherId().equals(user.id())) {
        throw new BusinessException(403, "无权查看未报考本人的学生");
    }
    return converter.toResponse(it);
}
```

Mapper 兜底：

```xml
<update id="confirmOwn">
    UPDATE intention
       SET status = '已确认'
     WHERE id = #{intentionId}
       AND student_id = #{studentId}
       AND status = '拟录取'
</update>
```

Service 判断 `affectedRows == 1`，否则抛 403 或 409，防止并发下状态被他人改变。

### 5.3 越权示例（学生篡改 intentionId）

场景：学生 `S2026001`（`stu-1`）已登录，把自己的 token 用于确认他人 `stu-2` 的意向 `int-4`。

```http
POST /api/student/confirm
Authorization: Bearer tok-student-stu-1-xxxx
Content-Type: application/json

{ "intentionId": "int-4" }
```

处理过程：

1. 拦截器校验 token 有效，角色为 `student`，允许访问 `/api/student/**`；
2. Controller 取 `LoginUser(id=stu-1)`，调用 `confirm("int-4", user)`；
3. Service 查询 `int-4`，发现 `student_id = stu-2`；
4. 抛出 `BusinessException(403, "无权操作他人的意向记录")`；
5. 全局异常处理器返回：

```json
{ "code": 403, "message": "无权操作他人的意向记录", "data": null }
```

6. 数据库未发生任何更新；`int-4` 仍为「已确认」。

同理，导师 A 请求 `/api/teacher/applicants/int-1`（该意向属于导师 B）返回 403；学生请求 `/api/admin/logs` 返回 403。

---

## 6. 参数校验与事务

### 6.1 校验分层

| 层次 | 手段 | 负责内容 | 失败码 |
| --- | --- | --- | --- |
| 语法校验 | Controller `@Valid` + Bean Validation | 必填、长度、范围、枚举、格式 | 400（统一转为参数错误） |
| 业务校验 | Service | 窗口是否开放、名额、状态机、归属、唯一性 | 410/409/422/403/404 |
| 数据库约束 | 唯一索引、外键/逻辑外键 | 账号唯一、重复提交兜底 | 409 或 422（按场景包装） |

> Controller 不重复写业务判断；Bean Validation 只做“字段级”规则。像“简介不少于 20 字”“最多 3 个平行意向”“只有待面试可录分”必须落在 Service，因为需要结合数据库/状态。

### 6.2 DTO 校验规则

| DTO | 字段 | 约束 |
| --- | --- | --- |
| `LoginRequest` | `username` / `password` | `@NotBlank` |
| `SubmitIntentionRequest` | `choices` | `@NotEmpty`、`@Size(max=3)`、元素 `@NotNull` |
|  | `intro` | `@NotBlank`、`@Size(min=20, max=500)` |
|  | `acceptInnerAdjust` / `acceptGlobalAdjust` | `Boolean`，服务端 `Boolean.TRUE.equals(...)` |
| `ConfirmRequest` | `intentionId` | `@NotNull` |
| `InterviewRequest` | `attitude/logic/coding/commitment` | `@NotNull`、`@Min(1)`、`@Max(10)` |
|  | `comment` | `@Size(max=500)` |
| `DecisionRequest` | `decision` | `@Pattern(regexp = "admit|waitlist|reject")` |
| `ImportRequest` | `type` | `@Pattern(regexp = "student|teacher")` |
|  | `rows` | `@NotEmpty` |
| `ScheduleUpdateRequest` | `startAt` / `endAt` | `@NotNull`，Service 校验开始早于结束 |
| `NoticeCreateRequest` | `title` / `content` | `@NotBlank`、`@Size(max=...)` |
| `QuotaUpdateRequest` | `quota` | `@NotNull`、`@Min(1)`、`@Max(20)` |
| `AdjustmentRunRequest` | `type` | `@Pattern(regexp = "inner|global|interview|supplement")` |

### 6.3 BusinessException 与全局异常

```java
public class BusinessException extends RuntimeException {
    private final int code;
    private final Object data;

    public BusinessException(int code, String message) {
        this(code, message, null);
    }
    public BusinessException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }
    public int getCode() { return code; }
    public Object getData() { return data; }
}
```

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getCode())
                .body(ApiResponse.fail(e.getCode(), e.getMessage(), e.getData()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst().map(FieldError::getDefaultMessage).orElse("参数错误");
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, msg, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleOther(Exception e) {
        log.error("服务异常", e);
        return ResponseEntity.status(500)
                .body(ApiResponse.fail(500, "服务异常，请稍后重试", null));
    }
}
```

异常映射：

| 异常 | code | message |
| --- | --- | --- |
| `BusinessException` | 自带 code | 自带 message |
| `MethodArgumentNotValidException` | 400 | 第一个字段错误提示 |
| `HttpMessageNotReadableException` | 400 | 请求体格式不合法 |
| `DuplicateKeyException` | 409 | 数据已存在，请勿重复提交 |
| 其他 `Exception` | 500 | 服务异常，请稍后重试 |

### 6.4 事务边界

- 所有写操作在 Service 方法上标注：

```java
@Transactional(rollbackFor = Exception.class)
```

- 查询方法使用 `@Transactional(readOnly = true)` 或不开事务。
- 事务内顺序：业务校验 → 锁/查名额 → 写业务表 → 写日志 → 返回结果。任一失败全部回滚。
- 不在 Controller 开事务；不跨 Service 拆分同一业务（避免自调用事务失效）。
- 需要一次写入多条意向（1~3 条）、导入多行账号、补录多人时，必须整体处于同一事务。

```java
@Service
public class IntentionService {
    @Transactional(rollbackFor = Exception.class)
    public List<IntentionResponse> submit(SubmitIntentionRequest req, LoginUser user) {
        // 1. 窗口/数量/简介/重复提交/导师存在性校验
        // 2. 组装 1~3 条意向（快照教师与实训室）
        // 3. intentionMapper.batchInsert(list)
        // 4. operationLogService.record(...)
        return list.stream().map(converter::toResponse).toList();
    }
}
```

### 6.5 与演示版一致性检查清单

| 检查项 | 要求 |
| --- | --- |
| 路径与方法 | 与 `server.js` 的 `routes` 表完全一致 |
| 状态值 | 「待面试 / 面试完成 / 拟录取 / 递补 / 已确认 / 已放弃 / 已录取 / 未录取」 |
| 成功码 | 200，`message` 使用演示版中文提示（可补充细节） |
| 失败码 | 400/401/403/404/409/410/422/500，与第 2.3 节一致 |
| 身份字段 | 学生/导师操作一律由 token 解析，不接受前端身份参数 |
| 名额 | 服务端实时统计，不采信前端 `remaining` |
| 时间窗口 | 以 `schedule.start_at/end_at` 为准，边界包含首尾 |
| 日志 | 关键写操作写 `operation_log`，与业务同事务 |
