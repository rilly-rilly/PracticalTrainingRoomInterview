# 04 核心业务规则与事务实现

> 适用范围：学生实训室进组选拔系统（Vue 3 + Java 17 / Spring Boot 3 + MyBatis + MySQL 8）。
> 规则基线：`frontend/src/mock/server.js` 与 `frontend/src/mock/seed.js`。本文描述真实后端必须落地的状态机、业务校验、事务与并发方案；所有状态中文值与前端展示保持一致。

---

## 1. 状态机

### 1.1 八个状态

| 状态值（库内与接口一致） | Java 常量 | 含义 | 是否占用名额 | 说明 |
| --- | --- | --- | --- | --- |
| 待面试 | `PENDING` | 意向已提交，等待导师面试 | 否 | 初始状态 |
| 面试完成 | `SCORED` | 导师已录入评分，等待决定 | 否 | 录分后进入 |
| 拟录取 | `ADMITTED` | 导师决定拟录取 | 是 | 等待学生确认；唯一时自动确认 |
| 递补 | `WAITLIST` | 进入实训室递补队列 | 否 | 有 `rank` 顺位 |
| 已确认 | `CONFIRMED` | 学生确认唯一去向 | 是 | 学生侧终态，等待公示 |
| 已放弃 | `GIVEN_UP` | 学生放弃或逾期未确认 | 否 | 学生侧终态 |
| 已录取 | `ENROLLED` | 公示无异议，正式入组 | 是 | 最终结果 |
| 未录取 | `REJECTED` | 导师拒绝 | 否 | 可进入调剂；原记录不再流转 |

**占用名额的状态集合**：

```java
public static final Set<String> OCCUPIED = Set.of("拟录取", "已确认", "已录取");
```

即任意实训室 `used = count(status in ('拟录取','已确认','已录取'))`。「递补」「未录取」「已放弃」「待面试」「面试完成」都不占名额。

### 1.2 允许跳转表

| 起始状态 | 目标状态 | 触发动作 | 操作者 | 前置条件 |
| --- | --- | --- | --- | --- |
| 待面试 | 面试完成 | 录入面试评分 | 导师 | 意向必须报考本人；4 项评分均为 1~10 整数；该意向尚无面试记录 |
| 面试完成 | 拟录取 | `decision=admit` | 导师 | 报考本人；实训室占用名额 < 配额 |
| 面试完成 | 递补 | `decision=waitlist` | 导师 | 报考本人；`rank = 同房间递补最大值 + 1` |
| 面试完成 | 未录取 | `decision=reject` | 导师 | 报考本人 |
| 拟录取 | 已确认 | 学生手动确认 / 唯一拟录取自动确认 | 学生 / 系统 | 手动确认时若该生有 ≥2 个拟录取，确认窗口必须开放；唯一拟录取在决定时自动确认 |
| 拟录取 | 已放弃 | 学生确认另一个意向 / 逾期未确认任务 | 学生 / 系统 | 学生选择其他方向时，其余拟录取全部置为已放弃；逾期任务将所有拟录取置为已放弃 |
| 递补 | 拟录取 | 分轮补录 / 逾期补位 | 系统 / 管理员 | 实训室仍有空余名额；按 `rank` 从小到大取 |
| 已确认 | 已录取 | 公示结束并正式入组 | 管理员 / 定时任务 | 公示期已满（演示可用 `force`）；公示无异议 |
| 未录取 | 拟录取（新记录） | 统筹调剂 | 管理员 | 该生全部意向均为未录取；对应调剂意愿为 true；目标实训室有名额；**新增 round=2 意向记录，不修改原记录** |
| 已放弃 | — | 无 | — | 学生侧终态，不可恢复 |
| 已录取 | — | 无 | — | 最终结果，不可变更 |
| 未录取 | — | 无直接流转 | — | 原记录保持未录取，由调剂新建记录 |

**非法跳转示例**（必须返回 422，而不是静默忽略）：

| 场景 | 当前状态 | 尝试动作 | 响应 |
| --- | --- | --- | --- |
| 重复录分 | 面试完成 | 再次录入评分 | 422「只有『待面试』状态可以录入面试考核」 |
| 未录分先决策 | 待面试 | 决定拟录取 | 422「只有『面试完成』状态可以决定是否录取」 |
| 递补学生自行确认 | 递补 | 学生确认 | 422「当前状态不允许确认：递补」 |
| 已确认后反悔 | 已确认 | 学生确认/放弃 | 422「该意向已确认，无需重复操作」 |
| 待面试直接确认 | 待面试 | 学生确认 | 422「当前状态不允许确认：待面试」 |
| 公示未结束提前入组 | 已确认 | 结束公示（非 force） | 422/410「没有可结束的公示」或窗口未满 |

### 1.3 对作业原文的落地解读

作业原文要求「只有待面试状态可以录入面试考核和决定是否录取」，而演示版把面试与录取拆成两步。真实后端按以下方式落地：

1. **录入面试考核**：前置状态严格为「待面试」；录分成功后状态变为「面试完成」。
2. **决定是否录取**：前置状态严格为「面试完成」；决策参数为 `admit / waitlist / reject`。
3. 因此原文应理解为「导师对某个意向的考核与录取链路，从『待面试』开始；在完成录分后，才允许作出录取决定」。
4. 这样既满足“待面试才能进入考核”，又避免“未面试直接录取”和“录取后重复改分”。
5. 若同一意向重复录分，返回 422；若未录分直接决策，返回 422。状态守卫必须由 Service 层统一执行。

### 1.4 状态机代码（Service 唯一入口）

```java
public final class IntentionStatus {
    public static final String PENDING = "待面试", SCORED = "面试完成", ADMITTED = "拟录取",
            WAITLIST = "递补", CONFIRMED = "已确认", GIVEN_UP = "已放弃",
            ENROLLED = "已录取", REJECTED = "未录取";
    public static final Set<String> OCCUPIED = Set.of(ADMITTED, CONFIRMED, ENROLLED);

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            PENDING, Set.of(SCORED), SCORED, Set.of(ADMITTED, WAITLIST, REJECTED),
            ADMITTED, Set.of(CONFIRMED, GIVEN_UP), WAITLIST, Set.of(ADMITTED),
            CONFIRMED, Set.of(ENROLLED)); // 已放弃/已录取无出边；未录取由调剂新建记录

    public static void assertCanScore(Intention it) {
        if (!PENDING.equals(it.getStatus()))
            throw new BusinessException(422, "只有「待面试」状态可以录入面试考核，当前为「" + it.getStatus() + "」");
    }
    public static void assertCanDecide(Intention it) {
        if (!SCORED.equals(it.getStatus()))
            throw new BusinessException(422, "只有「面试完成」状态可以决定是否录取，当前为「" + it.getStatus() + "」");
    }
    public static void transit(Intention it, String target) {
        if (!ALLOWED.getOrDefault(it.getStatus(), Set.of()).contains(target))
            throw new BusinessException(422, "当前状态不允许变更为「" + target + "」");
        it.setStatus(target);
    }
}
```

写库时再加一层 CAS（见第 5 节）：

```xml
<update id="updateStatusCas">
    UPDATE intention
       SET status = #{target}
     WHERE id = #{id}
       AND status = #{expected}
</update>
```

Service 判断 `affectedRows == 1`，否则返回 409/422「状态已变化，请刷新后重试」。

---

## 2. 逐条业务规则表

### 2.1 意向填报

| 编号 | 规则 | 实现要点 | 失败码 |
| --- | --- | --- | --- |
| F-01 | 必须在填报窗口内 | 读取 `schedule` 中 `key='intent'`，`now >= start_at && now <= end_at`；窗口缺失视为关闭 | 410，`data` 可带 window |
| F-02 | 至少 1 个、最多 3 个平行意向 | `choices.size()` 校验；Bean Validation 先挡，Service 再兜底 | 422 |
| F-03 | 同一导师不可重复填报 | `HashSet` 判重 | 422 |
| F-04 | 必须填写个人简介且不少于 20 字 | `intro.trim().length() >= 20`；建议上限 500 | 422 |
| F-05 | 本轮（round=1）不可重复提交 | `count(student_id, round=1) > 0` 拒绝；并发时锁学生行 | 422 |
| F-06 | 所选导师必须存在 | 按 ID 批量查询，缺失 ID 明确提示 | 400 |
| F-07 | 写入教师与实训室快照 | 保存 `teacher_name_snap / teacher_no_snap / room_id / room_name_snap` | — |
| F-08 | 同一次提交共用简介与调剂意愿 | 所有行使用同一 `intro`、`accept_inner_adjust`、`accept_global_adjust` | — |
| F-09 | 优先级按选择顺序 | `priority = index + 1`，从 1 开始 | — |
| F-10 | 初始状态 | 全部为「待面试」，`round=1`，`rank=0` | — |
| F-11 | 事务 | 1~3 条意向批量插入必须同事务，任一失败全回滚 | 500 |
| F-12 | 身份 | 只使用 token 中的 `student_id`，请求体不接受 `studentId` | 403 |

### 2.2 导师评分

| 编号 | 规则 | 实现要点 | 失败码 |
| --- | --- | --- | --- |
| T-01 | 只能评价报考本人的学生 | `intention.teacher_id == 当前导师 id` | 403 |
| T-02 | 只有「待面试」可录分 | `IntentionStatus.assertCanScore` | 422 |
| T-03 | 四项评分必须是 1~10 的整数 | `attitude/logic/coding/commitment`，`@Min(1)`、`@Max(10)`，Service 再校验 | 422 |
| T-04 | 总分由服务端求和 | `total = attitude + logic + coding + commitment`，不采信前端 `total` | — |
| T-05 | 录分与状态更新同事务 | `insert interview` + `update intention status=面试完成` | 500 |
| T-06 | 同一意向只允许一条面试记录 | 唯一索引 `uk_interview_intention(intention_id)` 兜底 | 409/422 |
| T-07 | 评语可选 | 去空格保存，建议 ≤500 字 | 400 |

### 2.3 录取决定与名额

| 编号 | 规则 | 实现要点 | 失败码 |
| --- | --- | --- | --- |
| D-01 | 只有「面试完成」可决策 | `IntentionStatus.assertCanDecide` | 422 |
| D-02 | 只能决定报考本人的学生 | `teacher_id` 归属校验 | 403 |
| D-03 | 决策枚举合法 | 仅 `admit / waitlist / reject` | 400 |
| D-04 | 拟录取必须服务端统计名额 | 锁房间行 + 统计占用状态，`used >= quota` 拒绝 | 409，`data={roomId,quota,used}` |
| D-05 | 不采信前端名额 | 不使用前端传来的 `remaining/quota` 做判断；只查库 | — |
| D-06 | 递补顺位从 1 开始 | `rank = 同房间 status='递补' 的最大 rank + 1` | — |
| D-07 | 唯一拟录取自动确认 | 拟录取后若该生全库仅 1 条「拟录取」，立即置为「已确认」并写自动确认记录 | — |
| D-08 | 拒绝进入未录取 | 状态置「未录取」，等待统筹调剂 | — |
| D-09 | 实训室必须存在 | 房间查询为空返回 400 | 400 |
| D-10 | 事务 | 名额校验 + 状态更新 + 自动确认 + 日志同一事务 | 500 |

### 2.4 拟录取确认

| 编号 | 规则 | 实现要点 | 失败码 |
| --- | --- | --- | --- |
| C-01 | 只有「拟录取」可确认 | 其它状态一律拒绝 | 422 |
| C-02 | 已确认/已放弃/已录取不可再操作 | 明确提示 | 422 |
| C-03 | 唯一拟录取自动确认 | 导师 admit 后立即执行；写 `mode=auto` 确认记录 | — |
| C-04 | ≥2 个拟录取必须限时选择唯一去向 | 确认窗口 `key='confirm'` 必须开放，否则 410 | 410 |
| C-05 | 确认一个，其余全部放弃 | 更新所选为「已确认」，其余该生「拟录取」全部置「已放弃」，写 `mode=giveup` 记录 | 500 |
| C-06 | 逾期视为放弃 | 定时任务/管理员手动触发：所有「拟录取」→「已放弃」，写 `mode=expired` | — |
| C-07 | 逾期后按顺位补位 | 对受影响实训室，按 `rank` 升序把「递补」→「拟录取」，并自动确认唯一情况 | — |
| C-08 | 确认不可重复 | 状态守卫 + CAS 更新 | 422/409 |
| C-09 | 学生只能确认自己的记录 | `intention.student_id == 当前学生` | 403 |

> 注意：如果学生有 2 个及以上拟录取且未在窗口内确认，逾期处理会把这些「拟录取」**全部**置为「已放弃」，而不是只放弃其中一个。演示版 `expireConfirmations` 即如此实现。

### 2.5 分轮补录

| 编号 | 规则 | 实现要点 | 失败码 |
| --- | --- | --- | --- |
| S-01 | 有「递补」且房间有空位才补 | `while (used < quota)` 逐轮补 | — |
| S-02 | 按递补顺位升序 | `ORDER BY rank ASC`，取第一条 | — |
| S-03 | 补录即拟录取 | 「递补」→「拟录取」，随后执行唯一拟录取自动确认 | — |
| S-04 | 名额满或无递补即停止 | 双重终止条件，避免死循环 | — |
| S-05 | 生成补录记录 | `adjustment`：`round=2`、`type=supplement`、`type_label=分轮补录` | — |
| S-06 | 全部在事务内 | 多人补录任一失败全回滚 | 500 |

### 2.6 统筹调剂（三级优先级）

| 优先级 | type | 含义 | 候选条件 | 目标选择 |
| --- | --- | --- | --- | --- |
| 第一 | `inner` | 实训室内部调剂 | 全部意向未录取；至少一条 `acceptInnerAdjust=true`；未执行过 inner | 仅限学生原填报的实训室；排除已报考导师；找该房间内仍有名额的导师 |
| 第二 | `global` | 全院跨实训室调剂 | 全部意向未录取；至少一条 `acceptGlobalAdjust=true`；未执行过 global | 原填报实训室之外的其他实训室；不排除原导师；找有名额的导师 |
| 第三 | `interview` | 约谈 | 全部意向未录取；所有意向均 `acceptInnerAdjust=false` 且 `acceptGlobalAdjust=false`；未生成过约谈 | 不分配导师，生成「待约谈，由学院统一安排」记录 |

| 编号 | 规则 | 实现要点 | 失败码 |
| --- | --- | --- | --- |
| A-01 | 候选必须「全部未录取」 | `list.length > 0 && every(status == 未录取)` | 无候选时 422 |
| A-02 | 同一学生同一类型只执行一次 | 检查已有 `adjustment(student_id,type)` | 跳过/不再生成 |
| A-03 | 调剂意愿来自意向记录 | `inner` 看 `acceptInnerAdjust`；`global` 看 `acceptGlobalAdjust` | 无候选 422 |
| A-04 | 目标导师必须有空余名额 | 房间 `quota > 0` 且 `used < quota`；inner 还要排除该生已报考导师 | — |
| A-05 | 调剂成功新建意向记录 | `round=2`、`priority=0`、`status=拟录取`、复用原简介与调剂意愿、写入新教师/房间快照 | — |
| A-06 | 原未录取记录不修改 | 保留历史，便于审计与展示 | — |
| A-07 | 自动确认 | 新记录使该生拟录取数=1 时自动确认 | — |
| A-08 | 无法调剂要说明原因 | `skipped: [{name, reason}]`；inner 为「本人填报的实训室均无名额」，global 为「全院其他实训室均无名额」 | — |
| A-09 | 执行顺序 | 先 `inner`，再 `global`，最后对仍不服从调剂者 `interview` | — |
| A-10 | 事务 | 一次调剂批次的所有学生变更在同一事务；任一失败全回滚 | 500 |

### 2.7 结果公示

| 编号 | 规则 | 实现要点 | 失败码 |
| --- | --- | --- | --- |
| P-01 | 可公示对象为「已确认」 | 每位学生取其「已确认」意向 | — |
| P-02 | 公示期 1 天 | `startAt=now`，`endAt=now+24h`，状态 `public` | — |
| P-03 | 无可公示对象拒绝 | 候选人数量为 0 | 422 |
| P-04 | 同一意向不重复公示 | 已存在 `status='public'` 且相同 `intentionId` 则跳过 | — |
| P-05 | 公示期满才能结束 | `now >= endAt`；演示可 `force=true` | 422 |
| P-06 | 结束后正式入组 | 公示记录 `public`→`final`；对应「已确认」意向→「已录取」 | — |
| P-07 | 已录取为最终结果 | 不再允许任何学生侧或导师侧变更 | 422 |

### 2.8 导入校验

| 编号 | 规则 | 实现要点 | 失败码 |
| --- | --- | --- | --- |
| I-01 | 导入类型合法 | `student` / `teacher` | 400 |
| I-02 | 至少一行数据 | `rows.size() > 0` | 422 |
| I-03 | 行号可用于定位 | Excel 第 1 行为表头，数据行从第 2 行起：`line = index + 2` | — |
| I-04 | 学生必填 | 姓名、学号、专业、年级 | 422 行级原因 |
| I-05 | 导师必填 | 姓名、工号、实训室编号；职称可缺省为「讲师」 | 422 行级原因 |
| I-06 | 实训室编号必须存在 | 校验 `room.code` 集合 | 422 行级原因 |
| I-07 | 学号/工号文件内唯一 | 大小写不敏感判重 | 422 行级原因 |
| I-08 | 学号/工号库内唯一 | 导入前查库 + 数据库唯一索引兜底 | 422/409 |
| I-09 | 严格模式 | 任一行失败 → 整体 422，不写任何数据，`data.failures` 带行号与原因 | 422 |
| I-10 | 非严格模式 | 跳过失败行，仅有效行入库 | 200 |
| I-11 | 无有效行拒绝 | 非严格模式有效行为 0 时也返回 422 | 422 |
| I-12 | 初始密码随机且只返回一次 | `Stu@` / `Teach@` + 6 位随机串；BCrypt 存哈希；明文字段只在响应出现 | — |
| I-13 | 导入批次与日志 | 记录总数、成功数、跳过数、操作者、时间；日志不含明文密码 | — |
| I-14 | 事务 | 写入有效行、批次、日志同事务；数据库异常整体回滚 | 500 |

---

## 3. 三条重点流程详细设计

### 3.1 流程一：意向填报

**时序**

```text
学生提交
  → 拦截器：token 有效 + role=student，得到 LoginUser(studentId)
  → Service：读取 intent 窗口（410）
  → 校验 1~3 个、无重复（422）
  → 校验简介 ≥20 字（422）
  → 校验本轮未提交（422；并发锁学生行）
  → 校验导师存在（400）
  → 组装快照 + priority + round=1 + status=待面试
  → 批量插入（事务）
  → 写 operation_log（同事务）
  → 返回当前学生意向列表
```

**Java 实现片段**

```java
@Transactional(rollbackFor = Exception.class)
public List<IntentionResponse> submit(SubmitIntentionRequest req, LoginUser user) {
    scheduleService.requireOpen("intent");            // 窗口关闭/缺失 → 410

    List<String> choices = req.choices();
    if (choices == null || choices.isEmpty() || choices.size() > 3)
        throw new BusinessException(422, "请选择 1~3 个平行意向");
    if (new HashSet<>(choices).size() != choices.size())
        throw new BusinessException(422, "同一导师不能重复填报");

    String intro = req.intro() == null ? "" : req.intro().trim();
    if (intro.length() < 20)
        throw new BusinessException(422, "个人简介不能为空且不少于 20 字");

    studentMapper.selectByIdForUpdate(user.id());      // 防并发重复提交
    if (intentionMapper.countByStudentAndRound(user.id(), 1) > 0)
        throw new BusinessException(422, "本轮意向已提交，不可重复填报");

    Map<String, Teacher> teachers = teacherMapper.selectByIds(choices).stream()
            .collect(Collectors.toMap(Teacher::getId, t -> t));
    for (String id : choices) {
        if (!teachers.containsKey(id))
            throw new BusinessException(400, "所选导师不存在：" + id);
    }

    List<Intention> rows = new ArrayList<>();
    for (int i = 0; i < choices.size(); i++) {
        Teacher t = teachers.get(choices.get(i));
        Room room = roomMapper.selectById(t.getRoomId());
        rows.add(Intention.submitted(user.id(), t, room, intro, i + 1,
                Boolean.TRUE.equals(req.acceptInnerAdjust()),
                Boolean.TRUE.equals(req.acceptGlobalAdjust())));
    }
    intentionMapper.batchInsert(rows);
    operationLogService.record("student", user.id(), user.name(), "提交意向",
            choices.size() + " 个平行意向 / 内部调剂 " + req.acceptInnerAdjust()
                    + " / 全院调剂 " + req.acceptGlobalAdjust());
    return rows.stream().map(converter::toResponse).toList();
}
```

> `Intention.submitted(...)` 内写入教师姓名/工号快照、实训室 ID/名称快照、`priority`、`round=1`、`status=待面试`、`rank=0`。快照必须与提交时一致；前端 `remaining` 仅展示，提交时不校验名额。

**要点**

- 快照必须与提交时一致，后续导师改名/实训室改名不影响历史意向展示。
- `priority` 仅用于第一轮平行意向展示与排序；调剂记录 `priority=0`。
- 同一学生一轮只能投一次，所有意向一次写入，不允许分多次追加。
- 前端展示的 `remaining` 只是提示，提交时不校验名额（名额在导师拟录取时校验）。

### 3.2 流程二：录取与名额控制

**核心原则**

1. 导师只能决定报考本人的意向，且当前必须是「面试完成」。
2. 名额以数据库实时统计为准，绝不使用前端传来的剩余名额。
3. 拟录取前先锁实训室行，再统计占用名额，避免两个导师并发超录。
4. 拟录取后若该生全库仅有 1 条拟录取，立即自动确认。
5. 递补顺位 `rank = 同房间递补最大值 + 1`，最小为 1。

**名额统计 SQL**

```sql
-- 先锁房间行，串行化同一实训室的录取决策
SELECT id, name, quota
  FROM room
 WHERE id = ?
 FOR UPDATE;

-- 再统计占用名额。当前意向尚未变成拟录取，因此不会被计入
SELECT COUNT(*)
  FROM intention
 WHERE room_id = ?
   AND status IN ('拟录取', '已确认', '已录取')
 FOR UPDATE;
```

> 只执行 `COUNT ... FOR UPDATE` 在计数为 0 时可能依赖索引间隙锁，边界不够直观；课程实现建议**先锁 room 行**，再统计。两者都使用 `FOR UPDATE`，并保持统一的加锁顺序，避免死锁。

**Java 实现片段**

```java
@Transactional(rollbackFor = Exception.class)
public DecisionResult decide(String intentionId, String decision, LoginUser user) {
    Intention it = intentionMapper.selectById(intentionId);
    if (it == null) throw new BusinessException(404, "意向记录不存在");
    if (!it.getTeacherId().equals(user.id()))
        throw new BusinessException(403, "只能决定报考本人的学生");
    IntentionStatus.assertCanDecide(it);
    if (!Set.of("admit", "waitlist", "reject").contains(decision))
        throw new BusinessException(400, "录取决定参数不合法");

    if ("admit".equals(decision)) {
        Room room = roomMapper.selectByIdForUpdate(it.getRoomId());      // 1. 锁房间行
        if (room == null) throw new BusinessException(400, "实训室不存在");
        int used = intentionMapper.countOccupiedByRoomForUpdate(it.getRoomId()); // 2. 服务端统计
        if (used >= room.getQuota())
            throw new BusinessException(409,
                    "名额已满：" + room.getName() + " 招收上限 " + room.getQuota()
                            + " 人，当前已占用 " + used + " 人（409）",
                    Map.of("roomId", room.getId(), "quota", room.getQuota(), "used", used));

        int affected = intentionMapper.updateStatusCas(intentionId, user.id(),
                IntentionStatus.SCORED, IntentionStatus.ADMITTED);       // 3. CAS 更新
        if (affected != 1) throw new BusinessException(409, "状态已变化，请刷新后重试");

        boolean auto = autoConfirmIfSingle(it.getStudentId());            // 4. 唯一拟录取自动确认
        operationLogService.record("teacher", user.id(), user.name(), "作出录取决定",
                it.getStudentNameSnap() + " / " + room.getName() + " / 拟录取");
        return new DecisionResult(IntentionStatus.ADMITTED, auto, null);
    }

    roomMapper.selectByIdForUpdate(it.getRoomId());                      // 递补顺位也要串行
    if ("waitlist".equals(decision)) {
        int rank = intentionMapper.maxWaitlistRankForUpdate(it.getRoomId()) + 1; // rank = max + 1
        int affected = intentionMapper.updateToWaitlistCas(intentionId, user.id(),
                IntentionStatus.SCORED, IntentionStatus.WAITLIST, rank);
        if (affected != 1) throw new BusinessException(409, "状态已变化，请刷新后重试");
        return new DecisionResult(IntentionStatus.WAITLIST, false, rank);
    }

    int affected = intentionMapper.updateStatusCas(intentionId, user.id(),
            IntentionStatus.SCORED, IntentionStatus.REJECTED);
    if (affected != 1) throw new BusinessException(409, "状态已变化，请刷新后重试");
    return new DecisionResult(IntentionStatus.REJECTED, false, null);
}

/** 全库该生仅 1 条拟录取时，立即置为已确认并写 auto 确认记录 */
private boolean autoConfirmIfSingle(String studentId) {
    if (intentionMapper.countAdmittedByStudent(studentId) != 1) return false;
    Intention only = intentionMapper.selectSingleAdmitted(studentId);
    intentionMapper.updateStatusCas(only.getId(), null,
            IntentionStatus.ADMITTED, IntentionStatus.CONFIRMED);
    confirmationMapper.insert(Confirmation.auto(studentId, only.getId(), LocalDateTime.now()));
    return true;
}
```

> 三种 decision 都必须写 `operation_log`（上例仅展示 admit 日志，waitlist/reject 同理）。`updateStatusCas` 的 WHERE 同时带 `id/teacher_id/expected_status`，影响行数为 0 即视为并发冲突，返回 409。

**说明**

- `updateStatusCas(id, teacherId, expected, target)` 中的 `teacherId` 也作为 WHERE 条件，防止越权与并发篡改。
- `autoConfirmIfSingle` 中 `updateStatusCas` 的教师条件传 `null` 时，SQL 不拼接教师条件；也可单独写 `confirmAuto` 语句。
- 若学生已有一个拟录取，导师再给该生第二个拟录取，则拟录取数为 2，不自动确认；学生需在确认窗口内选择。
- 递补 `rank` 在同一房间内唯一更稳妥，可加唯一索引 `uk_waitlist_rank(room_id, rank, status)`；`status` 参与唯一会带来更新复杂度，课程可用房间行锁保证。

### 3.3 流程三：管理员表格导入

**输入与字段**

| 类型 | 必填列 | 可选列 | 额外校验 |
| --- | --- | --- | --- |
| 学生 | 姓名、学号、专业、年级 | — | 学号库内/文件内唯一 |
| 导师 | 姓名、工号、实训室编号 | 职称（缺省「讲师」） | 工号库内/文件内唯一；实训室编号存在 |

**Java 实现片段**

```java
@Transactional(rollbackFor = Exception.class)
public ImportResultResponse importAccounts(ImportRequest req, LoginUser admin) {
    if (!Set.of("student", "teacher").contains(req.type()))
        throw new BusinessException(400, "导入类型不合法");
    if (req.rows() == null || req.rows().isEmpty())
        throw new BusinessException(422, "请提供待导入的数据行");
    boolean strict = Boolean.TRUE.equals(req.strict());

    Set<String> existingNo = typeStudent(req.type())
            ? studentMapper.selectAllNosUpper() : teacherMapper.selectAllNosUpper(); // 库内唯一
    Set<String> seenNo = new HashSet<>();                                        // 文件内唯一
    Set<String> roomCodes = roomMapper.selectAllCodes();
    List<ValidRow> valid = new ArrayList<>();
    List<ImportFailure> failures = new ArrayList<>();

    for (int i = 0; i < req.rows().size(); i++) {
        int line = i + 2;                                                       // 表头占第 1 行
        Map<String, String> row = req.rows().get(i);
        String name = trim(row.get("姓名"));
        String no = trim(typeStudent(req.type()) ? row.get("学号") : row.get("工号"));
        String upperNo = no.toUpperCase(Locale.ROOT);
        List<String> reasons = new ArrayList<>();
        if (name.isEmpty()) reasons.add("姓名缺失");
        if (no.isEmpty()) reasons.add(typeStudent(req.type()) ? "学号缺失" : "工号缺失");
        if (typeStudent(req.type())) {
            if (trim(row.get("专业")).isEmpty()) reasons.add("专业缺失");
            if (trim(row.get("年级")).isEmpty()) reasons.add("年级缺失");
        } else {
            String code = trim(row.get("实训室编号"));
            if (code.isEmpty()) reasons.add("实训室编号缺失");
            else if (!roomCodes.contains(code)) reasons.add("实训室编号不存在：" + code);
        }
        if (!no.isEmpty() && seenNo.contains(upperNo)) reasons.add("文件内账号重复：" + no);
        if (!no.isEmpty() && existingNo.contains(upperNo)) reasons.add("账号已存在：" + no);

        if (!reasons.isEmpty()) {                                               // 跳过并记录行号原因
            failures.add(new ImportFailure(line, no, name, reasons));
            if (!no.isEmpty()) seenNo.add(upperNo);
            continue;
        }
        seenNo.add(upperNo);
        valid.add(new ValidRow(name, no, trim(row.get("专业")),
                trim(row.get("年级")), trim(row.get("职称")), trim(row.get("实训室编号"))));
    }

    if (strict && !failures.isEmpty())                                          // 严格模式：未写库，整体回滚
        throw new BusinessException(422,
                "严格模式：共 " + failures.size() + " 行校验失败，本次导入整体回滚，未写入任何数据",
                ImportResultResponse.fail(req.rows().size(), failures, true));
    if (valid.isEmpty())
        throw new BusinessException(422, "没有可导入的有效数据，请检查必填字段与账号唯一性",
                ImportResultResponse.fail(req.rows().size(), failures, strict));

    List<AccountRow> accounts = new ArrayList<>();
    for (ValidRow r : valid) {
        String password = (typeStudent(req.type()) ? "Stu@" : "Teach@")
                + Long.toHexString(SECURE_RANDOM.nextLong()).substring(0, 6);   // 初始密码
        if (typeStudent(req.type())) {
            studentMapper.insert(Student.create(r, passwordEncoder.encode(password)));
        } else {
            teacherMapper.insert(Teacher.create(r, passwordEncoder.encode(password)));
        }
        accounts.add(new AccountRow(r.no(), r.name(), password));               // 明文仅本次返回
    }
    importBatchMapper.insert(ImportBatch.of(req.type(), admin.id(), req.rows().size(),
            valid.size(), failures.size(), strict));
    operationLogService.record("admin", admin.id(), admin.name(), "导入账号",
            "成功 " + valid.size() + " 条，跳过 " + failures.size() + " 条，未生成密码明文入库");
    return ImportResultResponse.ok(valid.size(), failures, accounts, strict);
}
```

**严格 / 非严格事务语义**

| 模式 | 校验失败 | 写库行为 | 响应 |
| --- | --- | --- | --- |
| `strict=true` | 任意行 | 一行都不写，事务直接抛 422 | 422 + `failures` |
| `strict=false` | 部分行 | 有效行入库，失败行跳过；写入阶段异常则整体回滚 | 200 + `failures` |
| 任意模式 | 有效行为 0 | 不写库 | 422 |
| 任意模式 | 数据库写入异常（如唯一索引冲突） | 整个事务回滚，避免半批数据 | 409/422/500 |

**密码与安全**

- 明文密码只在本次响应 `accounts[]` 中出现一次，前端应支持复制/下载；数据库只存 BCrypt 哈希。
- 日志与管理端列表不回显明文密码。
- 学号/工号入库前去空格，唯一性判断统一 `toUpperCase()`；数据库仍建唯一索引兜底。
- Excel 解析可单独放在 `ImportParser`，Service 只接收 `rows`，便于测试。

---

## 4. 事务与操作日志

### 4.1 事务范围

以下写操作必须在 Service 的 `@Transactional(rollbackFor = Exception.class)` 中完成：

| 业务 | 事务内动作 |
| --- | --- |
| 意向填报 | 锁学生行 → 校验 → 批量插入 1~3 条意向 → 写日志 |
| 面试评分 | 校验归属/状态 → 插入面试记录 → 更新状态 → 写日志 |
| 录取决定 | 锁房间 → 统计名额 → 更新意向 → 自动确认 → 写确认记录 → 写日志 |
| 学生确认 | 校验归属/状态/窗口 → 更新所选 → 其余置已放弃 → 写确认记录 → 写日志 |
| 逾期处理 | 拟录取→已放弃 → 写确认记录 → 递补→拟录取 → 自动确认 → 写补录记录 → 写日志 |
| 分轮补录 / 调剂 | 逐房间/逐学生更新 → 新增调剂意向 → 自动确认 → 写调剂记录 → 写日志 |
| 公示发布/结束 | 写公示 → 已确认→已录取 → 写日志 |
| 表格导入 | 校验 → 写入账号 → 写批次 → 写日志 |
| 名额/日程/公告 | 校验 → 更新 → 写日志 |

> 调剂和补录可能涉及多人，仍建议整批一个事务。任一学生处理失败时全部回滚，避免出现“部分学生已调剂、部分未调剂”的不一致状态。若数据量大，可改为按批次拆事务并增加批次状态，但课程规模无需如此。

### 4.2 operation_log 表

```sql
CREATE TABLE operation_log (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    operator_role VARCHAR(16)  NOT NULL COMMENT 'student/teacher/admin/system',
    operator_id   VARCHAR(32)  NOT NULL COMMENT '来自 token 的用户主键，不取前端参数',
    operator_name VARCHAR(64)  NOT NULL COMMENT '冗余姓名，便于展示',
    action        VARCHAR(64)  NOT NULL COMMENT '动作，如：提交意向、录入面试评分',
    detail        VARCHAR(512) NULL     COMMENT '业务明细，禁止写密码',
    create_time   DATETIME     NOT NULL,
    KEY idx_create_time (create_time),
    KEY idx_operator (operator_role, operator_id)
) ENGINE=InnoDB COMMENT='操作日志';
```

日志查询接口 `GET /api/admin/logs` 按 `create_time DESC` 返回最近 40 条。

### 4.3 日志服务

```java
@Service
public class OperationLogService {
    private final OperationLogMapper mapper;

    public void record(String role, String operatorId, String operatorName,
                       String action, String detail) {
        OperationLog log = new OperationLog();
        log.setOperatorRole(role);
        log.setOperatorId(operatorId);          // token 中的身份
        log.setOperatorName(operatorName);
        log.setAction(action);
        log.setDetail(detail);
        log.setCreateTime(LocalDateTime.now());
        mapper.insert(log);
    }
}
```

### 4.4 「先写日志再落库、同事务」示例

```java
@Transactional(rollbackFor = Exception.class)
public void decide(String intentionId, String decision, LoginUser teacher) {
    Intention it = intentionMapper.selectById(intentionId);
    checkOwnershipAndStatus(it, teacher);

    // 1. 先写操作日志（同一事务；日志失败则整个业务回滚）
    operationLogService.record("teacher", teacher.id(), teacher.name(),
            "作出录取决定", it.getStudentNameSnap() + " / " + it.getRoomNameSnap() + " / " + decision);

    // 2. 再落业务表
    int affected = intentionMapper.updateStatusCas(
            intentionId, teacher.id(), IntentionStatus.SCORED, IntentionStatus.ADMITTED);
    if (affected != 1) {
        throw new BusinessException(409, "状态已变化，请刷新后重试");
    }

    // 3. 自动确认、确认记录等后续写库仍在该事务内
    autoConfirmIfSingle(it.getStudentId());
}
```

**为什么同事务**

- 业务成功但日志缺失，会导致审计链断裂；日志写入失败但业务成功，会出现“无记录的录取”。
- 将日志与业务写入同一事务，可保证「有业务变更必有日志，有日志必有业务变更」。
- 日志切勿记录密码、token、完整身份证号等敏感信息；导入日志只记录数量。

---

## 5. 并发与边界处理

### 5.1 名额上限与超录

| 方案 | 说明 | 推荐度 |
| --- | --- | --- |
| 房间行悲观锁 `SELECT ... FOR UPDATE` | 同房间所有录取决策串行，统计准确，死锁风险低 | 推荐 |
| 统计 SQL 加 `FOR UPDATE` | 在统计上再加一层行/间隙锁 | 配合使用 |
| 条件更新 `UPDATE room SET reserved=reserved+1 WHERE reserved<quota` | 需维护冗余 `reserved` 计数，失败则 0 行 | 可选 |
| 唯一约束/乐观锁 | 适合防止重复记录，不适合直接表达名额 | 辅助 |

实现约定：

1. 所有涉及同一实训室名额的操作，统一按 `room` 行加锁，顺序一致：意向 → 房间 → 统计。
2. 统计口径固定：

```sql
SELECT COUNT(*)
  FROM intention
 WHERE room_id = #{roomId}
   AND status IN ('拟录取', '已确认', '已录取')
```

3. 不允许前端传入 `quota/remaining/used` 参与判断；前端只展示。
4. 名额减少时（管理员调低配额）必须校验 `quota >= used`，否则 409。
5. 补录、逾期补位也遵循同一统计口径，避免补录后又超录。

### 5.2 时间窗口与逾期

| 场景 | 处理 |
| --- | --- |
| 意向填报窗口开启判断 | `now >= start_at && now <= end_at`，与演示版边界一致（含首尾） |
| 确认窗口 | 学生有 ≥2 个拟录取时，必须 `confirm` 窗口开放，否则 410 |
| 唯一拟录取 | 不受确认窗口限制，在导师 admit 时立即自动确认 |
| 逾期任务 | 由 `@Scheduled` 定时任务在确认窗口结束后执行；管理员可手动触发 `/api/admin/admissions/expire` |
| 演示强制 | `force=true` 可绕过“窗口未结束”的限制，仅用于课堂演示 |
| 公示结束 | 到期后定时任务或管理员 `/api/admin/publicity/finalize`；`force=true` 仅演示 |
| 任务幂等 | 只扫描「拟录取」记录，处理后状态变为「已放弃」；重复执行不会重复处理 |

定时任务示例：

```java
@Scheduled(cron = "0 */10 * * * ?") // 每 10 分钟
public void expireAdmissionsJob() {
    ScheduleWindow w = scheduleService.get("confirm");
    if (w == null || LocalDateTime.now().isBefore(w.getEndAt())) {
        return;
    }
    admissionService.expirePendingConfirmations(false);
}
```

### 5.3 状态流转集中管理

- 所有状态变更只在 Service 调用 `IntentionStatus` 或专用状态方法。
- Controller 不出现 `setStatus`；Mapper 不写业务判断。
- 更新语句带“期望旧状态”，形成 CAS：

```xml
<update id="updateStatusCas">
    UPDATE intention
       SET status = #{target}
     WHERE id = #{id}
       AND status = #{expected}
       <if test="teacherId != null">
           AND teacher_id = #{teacherId}
       </if>
</update>
```

- 影响行数为 0 时，说明资源状态已被并发改变，返回 409「状态已变化，请刷新后重试」，不做静默成功。
- 学生确认、唯一自动确认、逾期放弃都必须使用 CAS，避免“重复确认”“重复放弃”。

### 5.4 其他边界

| 边界场景 | 预期行为 |
| --- | --- |
| 同一学生并发提交两次意向 | 锁学生行 + 本轮已提交校验，第二次 422 |
| 同一意向并发录分 | 唯一索引 + CAS，第二次 409/422 |
| 两个导师同时拟录取最后 1 个名额 | 房间行锁串行，后到者 409 |
| 管理员把配额调到低于已占用 | 409，提示当前已占用人数 |
| 逾期时学生有 2 个拟录取且都没确认 | 两条都置「已放弃」，再补位 |
| 递补学生被补录后自动确认 | 拟录取数=1 时立即「已确认」；若已有其他拟录取则不自动 |
| 调剂时目标房间刚被占满 | 锁房间 + 统计后无空位，记入 `skipped` 或重新选导师 |
| 全部未录取但没有调剂意愿 | `inner/global` 无候选返回 422；`interview` 生成约谈记录 |
| 公示期内提前结束（非 force） | 422，不允许 |
| 重复发布公示 | 已存在 public 记录的意向跳过，不重复插入 |
| 导入文件内重复且库内重复 | 两个原因可同时记录，行号明确 |
| 导入严格模式部分失败 | 422，`imported=0`，不写任何账号 |
| 非严格模式全部失败 | 422，`imported=0`，`failures` 完整返回 |
