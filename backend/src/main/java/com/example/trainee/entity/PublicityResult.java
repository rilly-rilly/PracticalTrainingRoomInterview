package com.example.trainee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("publicity_result")
public class PublicityResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long intentionId;
    @TableField("room_snapshot")
    private String roomSnapshot;
    @TableField("teacher_snapshot")
    private String teacherSnapshot;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private PublicityStatus status;
}
