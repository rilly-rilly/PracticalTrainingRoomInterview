package com.example.trainee.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 固定 8 个状态，值与数据库 ENUM 和 mock seed.js 完全对齐。
 */
public enum IntentionStatus {
    待面试, 面试完成, 拟录取, 递补, 已确认, 已放弃, 已录取, 未录取
}
