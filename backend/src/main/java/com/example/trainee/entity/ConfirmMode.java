package com.example.trainee.entity;

public enum ConfirmMode {
    auto,     // 学生仅此一个拟录取，系统自动确认
    manual,   // 学生手动选择唯一去向
    give_up,  // 选择唯一去向时自动放弃其他拟录取
    expired   // 逾期未确认，由管理员手动触发放弃
}
