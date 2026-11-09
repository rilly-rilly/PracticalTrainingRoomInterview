package com.example.trainee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("training_room")
public class TrainingRoom {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roomCode;
    private String name;
    private String direction;
    private Integer quota;
    @TableField("training_plan")
    private String trainingPlan;
    @TableField("assessment_requirement")
    private String assessmentRequirement;
    /** H2 兼容 VARCHAR，MySQL 正式库为 JSON */
    private String tags;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
