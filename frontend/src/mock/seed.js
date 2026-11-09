const HOUR = 3600 * 1000
const DAY = 24 * HOUR

/**
 * 演示用可复现哈希。真实后端使用 BCrypt，前端 mock 只为保证库里不出现明文密码。
 */
export function hashPassword(pw) {
  let h = 5381
  for (let i = 0; i < pw.length; i++) h = ((h << 5) + h + pw.charCodeAt(i)) >>> 0
  return 'demo$' + h.toString(36) + '$' + pw.length
}

const innerIntro =
  '我是计算机科学与技术专业 2024 级学生，熟悉 Python 与 C++，做过图像分类课程项目，希望在实训室系统学习视觉算法与工程落地。'
const innerIntro2 =
  '软件工程专业，前后端都写过，独立完成过校园二手交易平台，希望加入实训室参与真实项目，提升工程能力。'
const innerIntro3 =
  '人工智能专业，数学基础较好，跟进过目标检测开源项目，希望在导师指导下完成一个可复现的视觉实验。'
const innerIntro4 =
  '物联网工程专业，做过 STM32 小车与温湿度采集，熟悉串口通信，希望深入嵌入式系统与传感器网络。'
const innerIntro5 =
  '数据科学与大数据技术专业，熟悉 SQL 与 Pandas，做过电商用户行为分析，希望参与数据管道与可视化的完整流程。'
const innerIntro6 =
  '计算机专业，喜欢后端开发，了解 Spring Boot 与 MySQL，希望进组后参与接口设计与数据库优化。'
const innerIntro7 =
  '电子信息工程专业，做过 FPGA 与信号采集，愿意跨方向学习，希望获得一次院内调剂的面试机会。'
const innerIntro9 =
  '网络工程专业，熟悉 Linux 与网络协议，希望参与实训室的系统运维与后端开发工作。'

export function buildSeed() {
  const now = Date.now()
  const t = now

  const rooms = [
    {
      id: 'room-1',
      code: 'RV-01',
      name: '智能视觉实训室',
      direction: '计算机视觉 / 多模态感知',
      quota: 4,
      tags: ['机器视觉', '深度学习', '模型部署'],
      training: '入组后前 6 周完成 Python、OpenCV 与 PyTorch 基础训练，随后进入课题组参与工业缺陷检测项目，每周一次组会汇报。',
      assessment: '考核出勤率、每周任务完成度、组会汇报质量与期末项目答辩，连续两周未完成任务将退出实训室。',
      description: '面向工业检测与智能感知场景，配备 GPU 服务器与工业相机，与两家本地企业共建项目库。'
    },
    {
      id: 'room-2',
      code: 'RV-02',
      name: '嵌入式与物联网实训室',
      direction: '嵌入式系统 / 传感器网络',
      quota: 3,
      tags: ['STM32', 'RTOS', '物联网协议'],
      training: '从裸机开发到 RTOS 迁移，完成一套环境监测节点与网关的联调，参与学院实验室设备改造。',
      assessment: '考核硬件调试记录、代码规范、节点稳定性测试与最终联调演示。',
      description: '拥有示波器、逻辑分析仪与焊接工位，长期承接校园物联网设备维护任务。'
    },
    {
      id: 'room-3',
      code: 'RV-03',
      name: 'Web 应用开发实训室',
      direction: '全栈开发 / 工程化实践',
      quota: 3,
      tags: ['Vue 3', 'Spring Boot', 'MySQL'],
      training: '以真实业务需求为驱动，完成前后端分离项目从需求评审到上线的完整流程，参与代码评审。',
      assessment: '考核代码质量、接口设计、联调效率与项目上线后的运维记录。',
      description: '承接学院内部信息化需求，强调工程规范、版本管理与接口文档。'
    },
    {
      id: 'room-4',
      code: 'RV-04',
      name: '数据工程与智能分析实训室',
      direction: '数据管道 / 可视化分析',
      quota: 2,
      tags: ['数据清洗', '指标建模', '可视化'],
      training: '参与校园数据治理项目，完成数据采集、清洗、指标建模与看板搭建，每两周产出一次分析报告。',
      assessment: '考核数据质量、分析结论的可解释性与看板交付完整度。',
      description: '面向真实校园数据，强调数据口径、可复现分析与业务价值。'
    }
  ]

  const schedules = [
    { id: 'sch-1', key: 'open', step: 1, title: '实训室开放日与介绍', startAt: t - 7 * DAY, endAt: t - 1 * DAY, description: '各实训室开放参观，可在线查看项目方向、名额与培养安排。' },
    { id: 'sch-2', key: 'intent', step: 2, title: '学生意向填报', startAt: t - 1 * DAY, endAt: t + 2 * DAY, description: '最多填报 3 个平行意向，必须提交个人简介并选择调剂意愿。' },
    { id: 'sch-3', key: 'interview', step: 3, title: '导师面试考核', startAt: t - 2 * DAY, endAt: t + 1 * DAY, description: '导师查看报考学生简介，录入面试评分并决定拟录取或递补。' },
    { id: 'sch-4', key: 'confirm', step: 4, title: '拟录取确认', startAt: t - 1 * DAY, endAt: t + 3 * DAY, description: '收到 1 个拟录取自动确认；2 个及以上须自主选择唯一去向，逾期视为放弃。' },
    { id: 'sch-5', key: 'supplement', step: 5, title: '分轮补录', startAt: t + 2 * DAY, endAt: t + 4 * DAY, description: '按递补顺位补充空出的名额，逐轮公布补录结果。' },
    { id: 'sch-6', key: 'adjust', step: 6, title: '统筹调剂', startAt: t + 4 * DAY, endAt: t + 6 * DAY, description: '先后执行实训室内部调剂、全院跨实训室调剂，最后约谈未服从调剂学生。' },
    { id: 'sch-7', key: 'publicity', step: 7, title: '结果公示与入组', startAt: t + 6 * DAY, endAt: t + 7 * DAY, description: '汇总最终录取名单公示 1 天，无异议后正式入组。' }
  ]

  const notices = [
    { id: 'notice-1', title: '2026 年秋季实训室进组选拔正式启动', content: '本次共有 4 个实训室开放选拔，请同学们在意向填报窗口内完成填报，逾期不再受理。', level: '重要', publishedAt: t - 2 * DAY },
    { id: 'notice-2', title: '意向填报截止时间提醒', content: '意向填报窗口将于窗口结束后关闭，最多填报 3 个平行意向，务必填写真实个人简介。', level: '提醒', publishedAt: t - 1 * DAY },
    { id: 'notice-3', title: '面试安排说明', content: '导师将通过站内列表查看你的简介并组织面试，请保持通讯畅通，按导师通知的时间参加。', level: '普通', publishedAt: t - 12 * HOUR },
    { id: 'notice-4', title: '关于调剂意愿的说明', content: '勾选服从调剂可以增加录取机会：先进行实训室内部调剂，再进行全院跨实训室调剂，均不接受时将进入约谈环节。', level: '普通', publishedAt: t - 6 * HOUR }
  ]

  const teachers = [
    { id: 'tch-1', teacherNo: 'T1001', name: '李伯谦', title: '副教授', roomId: 'room-1', pwdHash: hashPassword('Teach@2026') },
    { id: 'tch-2', teacherNo: 'T1002', name: '王书宁', title: '讲师', roomId: 'room-1', pwdHash: hashPassword('Teach@2026') },
    { id: 'tch-3', teacherNo: 'T1003', name: '陈嘉禾', title: '副教授', roomId: 'room-2', pwdHash: hashPassword('Teach@2026') },
    { id: 'tch-4', teacherNo: 'T1004', name: '赵明远', title: '讲师', roomId: 'room-3', pwdHash: hashPassword('Teach@2026') },
    { id: 'tch-5', teacherNo: 'T1005', name: '孙倩', title: '高级工程师', roomId: 'room-4', pwdHash: hashPassword('Teach@2026') }
  ]

  const students = [
    { id: 'stu-1', studentNo: 'S2026001', name: '张一鸣', major: '计算机科学与技术', grade: '2024 级', pwdHash: hashPassword('Stu@2026') },
    { id: 'stu-2', studentNo: 'S2026002', name: '李思远', major: '软件工程', grade: '2024 级', pwdHash: hashPassword('Stu@2026') },
    { id: 'stu-3', studentNo: 'S2026003', name: '王雨桐', major: '人工智能', grade: '2023 级', pwdHash: hashPassword('Stu@2026') },
    { id: 'stu-4', studentNo: 'S2026004', name: '陈子豪', major: '物联网工程', grade: '2024 级', pwdHash: hashPassword('Stu@2026') },
    { id: 'stu-5', studentNo: 'S2026005', name: '刘嘉琪', major: '数据科学与大数据技术', grade: '2023 级', pwdHash: hashPassword('Stu@2026') },
    { id: 'stu-6', studentNo: 'S2026006', name: '周文轩', major: '计算机科学与技术', grade: '2024 级', pwdHash: hashPassword('Stu@2026') },
    { id: 'stu-7', studentNo: 'S2026007', name: '吴涵', major: '电子信息工程', grade: '2024 级', pwdHash: hashPassword('Stu@2026') },
    { id: 'stu-8', studentNo: 'S2026008', name: '郑欣然', major: '软件工程', grade: '2024 级', pwdHash: hashPassword('Stu@2026') },
    { id: 'stu-9', studentNo: 'S2026009', name: '韩雨泽', major: '网络工程', grade: '2023 级', pwdHash: hashPassword('Stu@2026') }
  ]

  const baseIntent = {
    teacherNameSnap: '',
    teacherNoSnap: '',
    roomId: '',
    roomNameSnap: '',
    round: 1,
    rank: 0
  }

  function intent(id, studentId, teacherId, priority, status, intro, acceptInner, acceptGlobal, submittedAt) {
    const teacher = teachers.find((x) => x.id === teacherId)
    const room = rooms.find((x) => x.id === teacher.roomId)
    return {
      ...baseIntent,
      id,
      studentId,
      teacherId,
      teacherNameSnap: teacher.name,
      teacherNoSnap: teacher.teacherNo,
      roomId: room.id,
      roomNameSnap: room.name,
      priority,
      status,
      intro,
      acceptInnerAdjust: acceptInner,
      acceptGlobalAdjust: acceptGlobal,
      submittedAt: submittedAt || t - 20 * HOUR,
      rank: 0
    }
  }

  const intentions = [
    intent('int-1', 'stu-1', 'tch-1', 1, '拟录取', innerIntro, true, true, t - 30 * HOUR),
    intent('int-2', 'stu-1', 'tch-4', 2, '拟录取', innerIntro, true, true, t - 30 * HOUR),
    intent('int-3', 'stu-1', 'tch-3', 3, '未录取', innerIntro, true, true, t - 30 * HOUR),

    intent('int-4', 'stu-2', 'tch-5', 1, '已确认', innerIntro2, true, true, t - 28 * HOUR),

    intent('int-5', 'stu-3', 'tch-1', 1, '递补', innerIntro3, true, false, t - 26 * HOUR),
    intent('int-6', 'stu-3', 'tch-2', 2, '未录取', innerIntro3, true, false, t - 26 * HOUR),
    intent('int-7', 'stu-3', 'tch-5', 3, '未录取', innerIntro3, true, false, t - 26 * HOUR),

    intent('int-8', 'stu-4', 'tch-3', 1, '待面试', innerIntro4, true, true, t - 18 * HOUR),
    intent('int-9', 'stu-4', 'tch-2', 2, '待面试', innerIntro4, true, true, t - 18 * HOUR),

    intent('int-10', 'stu-5', 'tch-4', 1, '面试完成', innerIntro5, true, true, t - 22 * HOUR),
    intent('int-11', 'stu-5', 'tch-5', 2, '面试完成', innerIntro5, true, true, t - 22 * HOUR),
    intent('int-12', 'stu-5', 'tch-2', 3, '面试完成', innerIntro5, true, true, t - 22 * HOUR),

    intent('int-13', 'stu-6', 'tch-2', 1, '未录取', innerIntro6, true, true, t - 24 * HOUR),
    intent('int-14', 'stu-7', 'tch-3', 1, '未录取', innerIntro7, false, true, t - 25 * HOUR),
    intent('int-15', 'stu-9', 'tch-4', 1, '未录取', innerIntro9, false, false, t - 23 * HOUR)
  ]

  intentions.find((x) => x.id === 'int-5').rank = 1

  const interviews = [
    { id: 'iv-1', intentionId: 'int-1', studentId: 'stu-1', teacherId: 'tch-1', attitude: 9, logic: 8, coding: 9, commitment: 8, total: 34, comment: '基础扎实，项目描述清楚，可进入工业检测课题组。', createdAt: t - 6 * HOUR },
    { id: 'iv-2', intentionId: 'int-2', studentId: 'stu-1', teacherId: 'tch-4', attitude: 8, logic: 7, coding: 8, commitment: 7, total: 30, comment: '工程经验不错，表达略紧张。', createdAt: t - 5 * HOUR },
    { id: 'iv-3', intentionId: 'int-4', studentId: 'stu-2', teacherId: 'tch-5', attitude: 9, logic: 9, coding: 8, commitment: 9, total: 35, comment: '数据基础与业务理解都好，直接进组。', createdAt: t - 10 * HOUR },
    { id: 'iv-4', intentionId: 'int-5', studentId: 'stu-3', teacherId: 'tch-1', attitude: 8, logic: 8, coding: 8, commitment: 8, total: 32, comment: '综合稳定，暂列递补第一顺位。', createdAt: t - 8 * HOUR },
    { id: 'iv-5', intentionId: 'int-10', studentId: 'stu-5', teacherId: 'tch-4', attitude: 8, logic: 8, coding: 9, commitment: 8, total: 33, comment: '前端工程能力突出，待确认录取。', createdAt: t - 9 * HOUR },
    { id: 'iv-6', intentionId: 'int-11', studentId: 'stu-5', teacherId: 'tch-5', attitude: 7, logic: 8, coding: 7, commitment: 6, total: 28, comment: '数据分析经验尚可，时间投入存疑。', createdAt: t - 10 * HOUR },
    { id: 'iv-7', intentionId: 'int-12', studentId: 'stu-5', teacherId: 'tch-2', attitude: 6, logic: 7, coding: 6, commitment: 7, total: 26, comment: '备选。', createdAt: t - 11 * HOUR }
  ]

  const confirmations = [
    { id: 'cf-1', studentId: 'stu-2', intentionId: 'int-4', mode: 'auto', confirmedAt: t - 9 * HOUR }
  ]

  const logs = [
    { id: 'log-1', operatorRole: 'system', operatorName: '系统', action: '初始化演示数据', detail: '生成 4 个实训室、5 名导师、9 名学生及演示意向', createdAt: t - 2 * DAY },
    { id: 'log-2', operatorRole: 'teacher', operatorName: '李伯谦', action: '录入面试评分', detail: '张一鸣 / 智能视觉实训室 / 总分 34', createdAt: t - 6 * HOUR },
    { id: 'log-3', operatorRole: 'teacher', operatorName: '孙倩', action: '作出录取决定', detail: '李思远 / 拟录取（自动确认）', createdAt: t - 9 * HOUR }
  ]

  return {
    version: 2,
    seededAt: t,
    seq: 100,
    admin: { id: 'adm-1', username: 'admin', name: '学院管理员', pwdHash: hashPassword('admin123') },
    rooms,
    teachers,
    students,
    intentions,
    interviews,
    confirmations,
    adjustments: [],
    publicities: [],
    schedules,
    notices,
    logs,
    sessions: [],
    imports: []
  }
}
