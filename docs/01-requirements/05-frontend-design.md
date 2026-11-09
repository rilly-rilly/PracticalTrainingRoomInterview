# 05 前端页面与五种反馈状态设计

> 文档定位：本文档说明前端页面/路由、路由守卫、Axios 封装、五种反馈状态（loading / empty / success / error / forbidden）以及演示数据机制，作为前端实现与联调的验收依据。
>
> 对齐代码：`frontend/src/router/index.js`、`frontend/src/auth.js`、`frontend/src/api/http.js`、`frontend/src/api/index.js`、`frontend/src/components/FeedbackState.vue`、`frontend/src/views/`。
>
> 技术栈：Vue 3 + Vite + Axios；学生端与导师/管理员后台共用同一套后端接口。

## 一、页面与路由清单

以下路由与 `frontend/src/router/index.js` 完全一致；`/403` 为公开的权限提示页，未匹配路径统一重定向到 `/`。

### 1.1 游客（无需登录，`meta.public: true`）

| 路由路径 | 页面 | 可见角色 | 主要数据来源接口 | 关键交互 |
| --- | --- | --- | --- | --- |
| `/` | 首页（Hero、7 步流程总览、重要通知、实训室速览） | 游客、学生、导师、管理员 | `GET /api/public/home` | 未登录显示「登录系统」「查看实训室介绍」；已登录显示「进入我的工作台」；流程步骤显示进行中/未开始/已结束；公告按时间倒序；实训室卡片显示剩余名额 |
| `/rooms` | 实训室介绍 | 游客、学生、导师、管理员 | `GET /api/rooms` | 浏览方向、名额、剩余名额、导师人数、培养安排、考核方式与简介；提供「返回首页」「登录系统」入口 |
| `/login` | 登录 | 游客、学生、导师、管理员 | `GET /api/public/demo-accounts`、`POST /api/auth/login`、`POST /api/public/reset` | 输入账号密码登录；演示账号一键填充；已登录时显示当前身份并提供「进入工作台 / 退出登录」；重置演示数据需二次确认 |
| `/403` | 无权限提示页 | 游客、学生、导师、管理员 | 无（纯静态页面） | 展示 403 含义与典型触发场景；提供「返回首页」「切换账号登录」 |

### 1.2 学生（`meta.roles: ['student']`）

| 路由路径 | 页面 | 可见角色 | 主要数据来源接口 | 关键交互 |
| --- | --- | --- | --- | --- |
| `/student/intent` | 意向填报 | 学生 | `GET /api/public/home`（填报窗口）、`GET /api/student/intentions`、`GET /api/rooms`、`GET /api/rooms/:id`（导师列表）、`POST /api/student/intentions` | 三个意向下拉（可只填 1 个、不可重复）；个人简介不少于 20 字；两个调剂意愿复选框；提交后展示只读意向表、简介与调剂意愿；窗口关闭提交触发 `410` |
| `/student/status` | 录取状态 | 学生 | `GET /api/student/status`、`POST /api/student/confirm`；演示调用 `GET /api/teacher/applicants` | 四张指标卡（意向数/拟录取数/递补状态/确认状态）；意向明细可展开简介；多个拟录取时单选并倒计时确认；单一拟录取显示自动确认；含「越权访问演示」验证 403 |
| `/student/adjustment` | 调剂与递补 | 学生 | `GET /api/student/adjustments` | 展示是否进入调剂池、两级调剂意愿、三级调剂优先级说明、调剂与补录记录（原实训室/去向/结果） |
| `/student/final` | 最终结果 | 学生 | `GET /api/student/final` | 阶段横幅（未填报/考核进行中/递补中/待公示/公示中/已录取）、目标实训室与导师、公示截止与倒计时、意向明细、调剂补录记录 |

### 1.3 导师（`meta.roles: ['teacher']`）

| 路由路径 | 页面 | 可见角色 | 主要数据来源接口 | 关键交互 |
| --- | --- | --- | --- | --- |
| `/teacher/applicants` | 报考学生与考核 | 导师 | `GET /api/teacher/applicants?status=`、`POST /api/teacher/decision` | 展示当前导师、实训室、名额占用与剩余；按「全部/待面试/面试完成/拟录取/递补/已确认/未录取」页签筛选并显示计数；展开行查看简介、调剂意愿与评分；对「面试完成」学生直接拟录取/递补/不录取；跳转面试详情 |
| `/teacher/interview/:id` | 面试考核 | 导师 | `GET /api/teacher/applicants/:id`、`POST /api/teacher/interviews`、`POST /api/teacher/decision` | 查看学生信息、实训室名额、简介与调剂意愿；四项评分（1~10 整数）与评语，实时总分（满分 40）；「面试完成」状态可作出录取决定，名额超限展示 `409` 详情 |

> 导航栏方面：导师端仅固定展示「报考学生与考核」，`/teacher/interview/:id` 由列表中的「录入评分 / 查看详情」进入，不单独出现在导航。

### 1.4 管理员（`meta.roles: ['admin']`）

| 路由路径 | 页面 | 可见角色 | 主要数据来源接口 | 关键交互 |
| --- | --- | --- | --- | --- |
| `/admin/import` | 表格导入与账号生成 | 管理员 | `POST /api/admin/import` | 学生/导师页签切换；粘贴 CSV 并解析预览；严格模式开关；开始导入；展示成功条数、失败明细与生成账号初始密码（可复制） |
| `/admin/schedule` | 选拔日程与公告 | 管理员 | `GET /api/admin/schedules`、`PUT /api/admin/schedules/:id`、`GET/POST/DELETE /api/admin/notices` | 编辑 7 个步骤的标题/开始/结束时间/说明并保存；发布公告（标题、级别、内容）与删除公告（二次确认） |
| `/admin/admissions` | 录取统筹 | 管理员 | `GET /api/admin/admissions`、`PUT /api/admin/rooms/:id/quota`、`POST /api/admin/admissions/expire` | 查看统计、各实训室名额与录取、各导师报考/拟录取/递补、全部意向明细、最近 30 条日志；调整名额上限；执行逾期未确认处理（支持演示强制） |
| `/admin/adjustment` | 补录与调剂 | 管理员 | `GET /api/admin/adjustments`、`POST /api/admin/adjustments/run` | 查看各实训室剩余名额；按固定优先级依次执行内部调剂、全院调剂、约谈；执行分轮补录；查看待调剂学生名单与调剂记录 |
| `/admin/publicity` | 结果公示 | 管理员 | `GET /api/admin/publicity`、`POST /api/admin/publicity/publish`、`POST /api/admin/publicity/finalize` | 查看待公示/公示中/已入组统计；发布 1 天公示；查看待公示名单与公示记录倒计时；结束公示并正式入组（支持演示提前结束） |

> 所有页面的导师姓名、实训室、意向状态、录取与调剂数据均来自上表接口；页面内只保留状态页签名称、CSV 模板表头、优先级说明等静态文案。

## 二、路由守卫设计

### 2.1 守卫规则

| 场景 | 判定依据 | 处理结果 |
| --- | --- | --- |
| 访问公开路由 | `to.meta.public === true`（`/`、`/rooms`、`/login`、`/403`） | 直接放行，不校验登录态 |
| 未登录访问受保护路由 | `authState.token` 为空 | 跳转 `/login?redirect=<to.fullPath>` |
| 已登录但角色不匹配 | `to.meta.roles` 不含 `authState.role` | 跳转 `/403` |
| 角色匹配且已登录 | `token` 存在且角色在允许列表 | 放行 |
| 访问不存在的路径 | 未命中任何路由 | 通配规则重定向到 `/` |

### 2.2 登录态存储与恢复

| 项目 | 设计 |
| --- | --- |
| 存储位置 | `localStorage`，键名 `trainee-selection-auth-v1` |
| 存储内容 | `{ token, role, profile }`；`profile` 含姓名、学号/工号、专业/职称、所属实训室等展示字段 |
| 刷新恢复 | 应用启动时 `main.js` 调用 `restoreAuth()`，从 `localStorage` 读取并填充响应式 `authState`，因此刷新页面不会丢失登录态 |
| 登录写入 | 登录成功后 `persistAuth(res.data)` 写入 `authState` 与 `localStorage`，随后跳转 `redirect` 或角色默认首页 |
| 主动退出 | 调用 `POST /api/auth/logout` 使旧令牌失效，再 `clearAuth()` 清除内存与 `localStorage`，跳转 `/login` |
| 接口 401 | Axios 响应拦截器调用 `clearAuth()`；`App.vue` 监听 `authState.token`，在非公开路由上自动跳转 `/login?redirect=<当前路径>` |
| 角色默认首页 | 学生 `/student/status`；导师 `/teacher/applicants`；管理员 `/admin/import`；其他 `/` |

> 前端路由守卫只负责体验与页面级隔离，真正的权限边界由后端按角色校验（mock 服务对越权接口返回 `403`）。因此即使绕过路由，也无法调用未授权接口。

## 三、Axios 封装设计

### 3.1 实例与拦截器

| 环节 | 设计 |
| --- | --- |
| 创建实例 | `axios.create({ baseURL: '', timeout: 10000 })`；演示期 `baseURL` 留空，接口路径均以 `/api` 开头 |
| 请求拦截器 | 从 `getToken()` 读取令牌，若存在则统一写入 `Authorization: Bearer <token>` |
| 成功响应 | 响应拦截器直接返回 `res.data`，即统一响应体 `{ code, message, data }`；页面通过 `res.data` 取业务数据 |
| 失败响应 | 从 `error.response.data` 提取 `{ code, message, data }`，组装为 `{ code, message, data }` 并 `Promise.reject` 给页面；同时触发全局 toast |
| 401 处理 | 拦截器内立即 `clearAuth()`，并通过 `App.vue` 的监听跳转登录页 |
| 全局提示 | `pushToast('[code] message', type, code)`；401/403 使用 error 样式，其余业务错误使用 warn 样式，约 3.8 秒后自动消失 |

统一响应体约定：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

成功时 `code=200`；失败时 `code` 与 HTTP 状态码保持一致（演示 mock 已按此实现），便于前端统一判断。

### 3.2 错误码映射

| code | 含义 | 典型触发场景 | 前端处理 |
| --- | --- | --- | --- |
| 401 | 未登录 / 登录态无效 | 令牌缺失、过期、退出后继续请求 | 清除登录态；全局 error toast「登录态无效，请重新登录」；跳转 `/login?redirect=` |
| 403 | 无权限 | 学生调用导师接口、导师调用管理员接口、查看/确认他人意向 | 全局 error toast；路由守卫场景直接进入 `/403`；页面内可展示权限说明 |
| 409 | 名额已满 | 导师拟录取时实训室已占用达到上限；名额上限低于已占用人数 | 在决策行/表单内展示「名额上限 / 当前已占用」，全局 warn toast 辅助提示 |
| 410 | 时间窗口关闭 | 意向填报窗口关闭后提交；拟录取确认窗口关闭后确认 | 在表单或确认卡片内展示 `form-error`，按钮置灰/提示已截止，全局 warn toast 辅助 |
| 422 | 状态不允许或校验失败 | 非「待面试」录分、非「面试完成」决策、重复确认、超过 3 个意向、简介不足 20 字、导入无有效行 | 表单内 `form-error`、决策行内 `decisionErrors` 或导入失败明细；全局 warn toast 辅助 |
| 400 | 参数错误 | 导入类型不合法、录取决定参数不合法、时间格式不合法 | 表单内提示或全局 toast |
| 404 | 资源不存在 | 意向记录不存在、实训室不存在、公告不存在、接口不存在 | `FeedbackState` 错误态 + 「重新加载」；全局 toast |
| 500 | 服务错误 | 后端未捕获异常 | `FeedbackState` 错误态 + 重试；全局 toast「服务内部错误」 |

### 3.3 全局 toast + 页面内提示双通道

| 通道 | 载体 | 适用场景 | 设计原则 |
| --- | --- | --- | --- |
| 全局 toast | `ToastHost` + `pushToast`，右上角悬浮 | 所有请求的通用反馈，尤其是 401/403/500 | 由响应拦截器统一触发，页面无需重复弹窗；带 `[code]` 便于演示与排障 |
| 页面内提示 | `form-error`、`form-success`、`hint--danger`、`decisionErrors`、`FeedbackState` error 态 | 需要用户修正输入、查看名额/失败明细或重试加载 | 409/410/422 以页面内为主，保留上下文；反馈状态页以 `FeedbackState` 为主，提供重试 |

双通道同时存在：错误发生时既有全局 toast，也有页面内可定位的提示；不依赖单一提示，避免用户错过关键错误。

### 3.4 接口模块组织

`frontend/src/api/index.js` 按角色拆分：

| 模块 | 主要方法 |
| --- | --- |
| `authApi` | `login`、`logout`、`me`、`demoAccounts`、`resetDemo` |
| `publicApi` | `home`、`rooms`、`room` |
| `studentApi` | `intentions`、`submit`、`status`、`confirm`、`adjustments`、`final` |
| `teacherApi` | `applicants`、`applicant`、`score`、`decide` |
| `adminApi` | `importData`、`schedules`、`updateSchedule`、`notices`、`createNotice`、`deleteNotice`、`admissions`、`updateQuota`、`expire`、`adjustments`、`runAdjustment`、`publicity`、`publish`、`finalize`、`logs` |

## 四、五种反馈状态设计

### 4.1 FeedbackState 组件

`frontend/src/components/FeedbackState.vue` 统一封装 loading / empty / error 三态，success 由默认插槽承载，forbidden 由 `/403` 页面与页面内权限提示承载。

| props / 能力 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `loading` | Boolean | `false` | 展示加载中态（spinner + 文案） |
| `error` | Object | `null` | 需包含 `code`、`message`；展示失败态与「重新加载」按钮 |
| `empty` | Boolean | `false` | 展示空数据态与 `emptyText` |
| `emptyText` | String | `暂无数据` | 空态说明文案 |
| `loadingText` | String | `正在加载数据…` | 加载态标题文案 |
| `errorTitle` | String | `加载失败` | 失败态标题 |
| `@retry` | Emit | — | 点击「重新加载」时触发，页面重新请求 |
| 默认插槽 | Slot | — | 三态均不成立时展示正常内容（success） |
| `empty-action` 插槽 | Slot | — | 空态下的操作按钮（如「前往意向填报」） |

渲染优先级：`loading` → `error` → `empty` → 默认插槽。也就是说，请求中永远不展示旧数据；请求失败优先展示可重试的错误态；无数据时展示带引导的空态。

```vue
<FeedbackState
  :loading="loading"
  :error="error"
  :empty="!loading && !error && items.length === 0"
  loading-text="正在加载数据…"
  empty-text="暂无数据"
  error-title="加载失败"
  @retry="load"
>
  <template #empty-action>
    <router-link class="btn btn--sm" to="/student/intent">前往意向填报</router-link>
  </template>

  <!-- success：正常业务内容 -->
</FeedbackState>
```

样式约定：`.state` 为状态容器；`.spinner` 为加载动画；`.state--error` 为浅红失败背景；`.state--empty` 为虚线空态；`.state__code` 展示 `状态码 N`；`.state__icon` 为圆形图标。

除组件三态外，动作级反馈采用局部状态，避免整页 loading：

| 动作级反馈 | 表现 |
| --- | --- |
| 提交/保存中 | 按钮禁用并显示「提交中…」「保存中…」「发布中…」「处理中…」 |
| 表单校验失败 | 请求前在 `form-error` 展示校验原因，不发起请求 |
| 决策失败（409 等） | 在对应学生行/卡片内展示 `decisionErrors[id]`，保留其他行操作 |
| 名额保存失败 | 在对应实训室行内展示 `quotaErrors[room.id]` |
| 导入执行中 | 独立 `state` + spinner，提示「正在导入，请稍候…」 |
| 成功操作 | 全局 success toast + 页面数据刷新；导入成功额外展示结果横幅 |

### 4.2 各页面五态展示建议

| 页面 | 加载中 | 空数据 | 成功 | 失败 | 无权限 |
| --- | --- | --- | --- | --- | --- |
| `/` 首页 | 「正在加载首页数据…」 | 「首页内容尚未配置，请稍后重试或联系管理员」 | Hero + 流程步骤 + 公告 + 实训室速览 | `FeedbackState` 失败态 + 重新加载；401/403 由全局提示处理 | 公开页面，无权限限制 |
| `/rooms` 实训室介绍 | 「正在加载实训室列表…」 | 「暂无可展示的实训室，请等待管理员配置」 | 实训室卡片（名额、剩余、培养与考核） | `FeedbackState` 失败态 + 重新加载 | 公开页面，无权限限制 |
| `/login` 登录 | 演示账号区「正在加载演示账号…」 | 「暂无可用的演示账号，请尝试重置演示数据」 | 演示账号列表可一键填充；登录成功跳转 | 登录失败 `form-error` 展示 `[401]`；演示账号加载失败在区域内提示并可重试 | 公开页面，无权限限制 |
| `/student/intent` 意向填报 | 「正在加载数据…」 | 未填报时展示填报表单；已提交后展示只读意向表 | 提交成功 toast + 只读意向表、简介、调剂意愿 | 加载失败可重试；提交失败 `form-error` 展示 `[410]/[422]` | 非学生访问由路由守卫跳 `/403` |
| `/student/status` 录取状态 | 「正在加载数据…」 | 「当前账号还没有提交任何意向」+「前往意向填报」 | 指标卡 + 意向明细 + 确认卡片/自动确认提示 | 加载失败可重试；确认失败 `form-error`；越权演示展示 `hint--danger` `403` | 路由守卫跳 `/403`；接口越权返回 `403` 并 toast |
| `/student/adjustment` 调剂与递补 | 「正在加载数据…」 | 「暂无调剂记录…」 | 调剂池状态 + 两级意愿 + 三级优先级 + 记录表 | 加载失败可重试 | 路由守卫跳 `/403` |
| `/student/final` 最终结果 | 「正在加载数据…」 | 无意向时提示并给出填报入口 | 阶段横幅 + 意向明细 + 调剂补录记录 | 加载失败可重试 | 路由守卫跳 `/403` |
| `/teacher/applicants` 报考学生 | 列表区「正在加载数据…」 | 「当前筛选条件下暂无报考学生」 | 导师/名额信息 + 页签筛选 + 学生列表 + 展开详情 | 列表加载失败 `FeedbackState` + 重试；决策失败行内 `decisionErrors`（含 409 名额） | 非导师跳 `/403`；查看他人明细返回 `403` |
| `/teacher/interview/:id` 面试考核 | 「正在加载数据…」 | 「未找到该报考记录」 | 学生信息 + 名额 + 简介 + 评分表单/已评分 + 录取决定 | 加载失败可重试；评分/决策失败 `form-error`（409 展示上限与已占用） | 非导师跳 `/403`；非本人记录返回 `403` |
| `/admin/import` 导入账号 | 解析「正在解析 CSV…」；导入「正在导入，请稍候…」 | 「请先粘贴 CSV 内容并点击解析预览」 | 结果横幅（成功/跳过/模式）+ 失败明细 + 生成账号与初始密码 | 解析错误 `form-error`（表头缺失等）；导入失败 `hint--danger` + 失败明细表 | 非管理员跳 `/403` |
| `/admin/schedule` 日程与公告 | 日程区/公告区分别 loading | 「暂无可维护的日程步骤」「暂无公告，可在右侧发布第一条通知」 | 7 步可编辑表格 + 公告列表 | 加载失败 `FeedbackState` + 重试；保存/删除失败 toast | 非管理员跳 `/403` |
| `/admin/admissions` 录取统筹 | 「正在加载数据…」 | 「暂无可统筹的录取数据」 | 统计卡 + 实训室名额表 + 导师表 + 意向明细 + 日志 | 加载失败可重试；名额保存失败行内 `quotaErrors`；逾期处理失败 `form-error` | 非管理员跳 `/403` |
| `/admin/adjustment` 补录与调剂 | 「正在加载数据…」 | 「暂无可调剂的数据」；子区块分别给出空态 | 名额可用性 + 优先级说明 + 四类操作 + 学生名单 + 调剂记录 | 加载失败可重试；执行失败 `form-error`（400/422） | 非管理员跳 `/403` |
| `/admin/publicity` 结果公示 | 「正在加载数据…」 | 「暂无可公示的录取数据」；子区块分别给出空态 | 统计卡 + 发布按钮 + 待公示名单 + 公示记录 + 结束公示 | 加载失败可重试；发布/结束失败 `form-error`（422） | 非管理员跳 `/403` |
| `/403` 无权限 | 不适用（静态页） | 不适用 | 403 图标 + 典型场景 + 返回首页/切换账号 | 不适用 | 本页即无权限态，不重复拦截 |

### 4.3 导入页专项说明

导入页是唯一需要展示「部分成功 + 明细 + 敏感初始密码」的页面，状态设计如下：

| 区块 | 展示内容 | 关键规则 |
| --- | --- | --- |
| 解析预览 | 行号 + 模板列；CSV 表头缺失时提示缺少列名 | 支持英文逗号、中文逗号、制表符；空行跳过；切换学生/导师页签会清空文本、预览与结果 |
| 执行中 | spinner + 「正在导入，请稍候…」+ 防重复提交说明 | 按钮禁用；导入中不展示旧结果 |
| 成功横幅 | 「导入完成」+ 服务端 message（成功 X 条 / 跳过 Y 条）+ 成功生成 / 跳过 / 导入模式（严格或宽松） | 成功条数来自接口 `imported`，跳过条数来自 `skipped` |
| 失败明细 | 表格列：行号 `/` 账号 `/` 姓名 `/` 原因；原因可能包含姓名缺失、学号/工号缺失、专业/年级缺失、实训室编号不存在、文件内账号重复、账号已存在 | 严格模式下任一行失败则整体回滚，错误提示明确「本次未写入任何数据」；宽松模式仅跳过失败行 |
| 生成账号 | 表格列：学号/工号、姓名、初始密码、实训室（导师）、操作（复制） | 初始密码只在本次导入结果中展示一次；数据库中仅保存密码哈希，刷新或离开页面后无法再次查看；复制成功/失败分别 toast |
| 失败态 | `hint--danger` 展示 message + `状态码 N`；无有效数据时同样归类为 `422` | 失败明细来自 `error.data.failures`；成功结果的失败明细来自 `result.failures` |

## 五、数据来源与演示版 mock 机制

### 5.1 页面不写死业务数据

- 页面中不出现写死的导师、意向和录取数据：导师列表从 `/api/rooms` 与 `/api/rooms/:id` 的 `teachers` 获取；意向、分数、录取、递补、调剂、公示均来自对应接口。
- 登录页的演示账号也由 `GET /api/public/demo-accounts` 返回，不在页面内写死账号清单。
- 页面内允许存在的静态常量仅限：状态页签名称、CSV 模板表头、三级调剂说明文案、空态/错误文案、评分维度名称。
- 所有页面在接口返回前不展示伪造数据；加载中、空数据、失败均按第五节的状态规范展示。

### 5.2 演示版 Axios 自定义 adapter

当前仓库可直接运行，是因为未启动真实后端，而是由 Axios 自定义 adapter 把请求转发到本地 mock 服务：

| 项目 | 现状 |
| --- | --- |
| 入口 | `frontend/src/api/http.js` 中设置 `instance.defaults.adapter` |
| 转发目标 | `frontend/src/mock/server.js` 的 `handleRequest(config)` |
| 延迟模拟 | 每次请求等待 `220 + 随机 180` 毫秒，模拟网络耗时 |
| 统一响应 | 返回 `{ code, message, data }`；错误时 HTTP 状态码与 `code` 一致，Axios 因此进入失败分支 |
| 路由与鉴权 | mock 服务内置与真实后端一致的接口路由表，按 `Authorization: Bearer` 解析会话并校验角色 |
| 数据持久化 | `frontend/src/mock/db.js` 将演示数据写入 `localStorage`，键名 `trainee-selection-demo-v2`；首次运行自动生成 seed |
| 接口一致性 | 接口路径、请求方法、请求体、统一响应与真实 Spring Boot 后端保持一致 |

### 5.3 接入真实后端

| 步骤 | 操作 |
| --- | --- |
| 1 | 将 `axios.create` 的 `baseURL` 替换为 Spring Boot 服务地址（或通过 Vite 代理把 `/api` 转发到后端），无需修改页面代码 |
| 2 | 移除 `frontend/src/api/http.js` 中的 `instance.defaults.adapter` 演示转发代码，以及 `../mock/server` 的导入 |
| 3 | 保留请求/响应拦截器：`Authorization: Bearer <token>`、统一解包 `{code,message,data}`、401 清除登录态、全局 toast |
| 4 | 真实后端应让错误响应的 HTTP 状态码与 `code` 一致；若后端统一返回 HTTP 200，则需在响应拦截器补充 `body.code !== 200` 的拒绝分支，否则错误不会进入失败态 |
| 5 | `/api/public/demo-accounts` 与 `/api/public/reset` 为演示专用接口，生产环境应移除或禁用，登录页的演示账号区同步下线 |

> 结论：页面层只依赖 `frontend/src/api/index.js` 的方法与统一响应结构；切换到真实后端属于基础设施替换，不涉及页面重写。

## 六、演示账号与数据说明

### 6.1 演示账号

| 角色 | 账号 | 密码 | 演示状态说明 |
| --- | --- | --- | --- |
| 管理员 | `admin` | `admin123` | 学院管理员，可进入全部后台页面 |
| 导师 | `T1001` | `Teach@2026` | 李伯谦（智能视觉实训室），可查看报考本人的学生 |
| 学生 | `S2026001` | `Stu@2026` | 张一鸣：2 个拟录取，待自主选择唯一去向 |
| 学生 | `S2026002` | `Stu@2026` | 李思远：1 个拟录取，已自动确认 |
| 学生 | `S2026003` | `Stu@2026` | 王雨桐：递补第 1 顺位 |
| 学生 | `S2026004` | `Stu@2026` | 陈子豪：待面试 |
| 学生 | `S2026005` | `Stu@2026` | 刘嘉琪：3 个面试完成，待导师决定 |
| 学生 | `S2026006` | `Stu@2026` | 周文轩：未录取，服从实训室内部调剂 |
| 学生 | `S2026007` | `Stu@2026` | 吴涵：未录取，仅服从全院跨实训室调剂 |
| 学生 | `S2026008` | `Stu@2026` | 郑欣然：未填报，可演示填报窗口 |
| 学生 | `S2026009` | `Stu@2026` | 韩雨泽：未录取，不服从调剂，可演示第三优先级约谈 |

> seed 数据中另有导师 `T1002`~`T1005` 与 4 个实训室；登录页演示账号区仅推荐 `T1001` 作为导师演示入口，其他导师账号同样可使用同一初始密码登录。

### 6.2 登录页一键填充与重置

| 能力 | 说明 |
| --- | --- |
| 一键填充 | 点击演示账号卡片即把账号与密码填入登录表单，不直接提交；用户可确认后点击登录 |
| 角色跳转 | 登录成功后按角色进入默认工作台；若 URL 带 `redirect` 且非空，则优先跳转该地址 |
| 重置演示数据 | 点击「重置演示数据」弹出确认框；确认后调用 `POST /api/public/reset`，重建 seed、清除当前登录态、重新加载演示账号并提示成功 |
| 数据持久化 | 演示数据保存在 `localStorage`（`trainee-selection-demo-v2`），刷新页面不会丢失；重置或清除浏览器本地存储可恢复初始状态 |
| 登录态持久化 | 登录态保存在 `localStorage`（`trainee-selection-auth-v1`），刷新可恢复；退出登录或 401 会清除 |

### 6.3 联调验收清单

| 检查项 | 预期结果 |
| --- | --- |
| 未登录访问 `/student/status` | 跳转 `/login?redirect=/student/status`，登录后回到该页 |
| 学生访问 `/admin/import` | 路由守卫跳转 `/403`；即使直接调用管理员接口也返回 `403` |
| 刷新任意受保护页面 | 登录态从 `localStorage` 恢复，页面正常展示 |
| 令牌失效后请求接口 | 返回 `401`，清除登录态并跳转登录页，同时出现全局 toast |
| 导师名额已满时拟录取 | 返回 `409`，页面展示名额上限与已占用人数 |
| 意向窗口关闭后提交 | 返回 `410`，表单内展示错误提示，不写入意向 |
| 导入含重复账号的表格（严格模式） | 返回 `422`，展示失败明细，且不生成任何账号 |
| 空数据页面 | 展示空态文案与引导按钮，不展示伪造数据 |
| 接入真实后端后 | 仅替换 `baseURL` 与移除 mock adapter，页面与交互保持不变 |
