package com.example.trainee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("schedule_item")
public class ScheduleItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("step_no")
    private Integer stepNo;
    @TableField("key_value")
    private String keyValue;
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String description;
    private LocalDateTime updateTime;
}
