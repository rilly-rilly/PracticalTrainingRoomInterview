package com.example.trainee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview")
public class Interview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long intentionId;
    @TableField("attitude_score")
    private Integer attitudeScore;
    @TableField("logic_score")
    private Integer logicScore;
    @TableField("coding_score")
    private Integer codingScore;
    @TableField("commitment_score")
    private Integer commitmentScore;
    private Integer total;
    private String comment;
    private LocalDateTime createTime;
}
