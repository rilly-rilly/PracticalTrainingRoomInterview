package com.example.trainee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("adjustment_record")
public class AdjustmentRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Integer round;
    private AdjustmentType type;
    @TableField("from_room_snapshot")
    private String fromRoomSnapshot;
    @TableField("to_room_snapshot")
    private String toRoomSnapshot;
    @TableField("to_teacher_snapshot")
    private String toTeacherSnapshot;
    @TableField("result_snapshot")
    private String resultSnapshot;
    @TableField("operator_role")
    private String operatorRole;
    @TableField("operator_id")
    private Long operatorId;
    private LocalDateTime createTime;
}
