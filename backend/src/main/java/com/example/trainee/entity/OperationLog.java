package com.example.trainee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("operator_role")
    private String operatorRole;
    @TableField("operator_id")
    private Long operatorId;
    @TableField("operator_name")
    private String operatorName;
    private String action;
    private String detail;
    @TableField("create_time")
    private LocalDateTime createTime;
}
