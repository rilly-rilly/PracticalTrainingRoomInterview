package com.example.trainee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("intention")
public class Intention {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long teacherId;
    private Long roomId;
    @TableField("teacher_name_snapshot")
    private String teacherNameSnapshot;
    @TableField("teacher_no_snapshot")
    private String teacherNoSnapshot;
    @TableField("room_name_snapshot")
    private String roomNameSnapshot;
    private Integer priority;
    private String intro;
    @TableField("accept_inner_adjust")
    private Integer acceptInnerAdjust; // 0/1
    @TableField("accept_global_adjust")
    private Integer acceptGlobalAdjust; // 0/1
    private Integer round;
    private IntentionStatus status;
    @TableField("submit_time")
    private LocalDateTime submitTime;
    @TableField("waitlist_rank")
    private Integer waitlistRank;
    private LocalDateTime updateTime;
}
