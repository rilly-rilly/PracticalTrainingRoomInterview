package com.example.trainee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("admission_confirmation")
public class AdmissionConfirmation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long intentionId;
    private ConfirmMode mode;
    private LocalDateTime confirmTime;
}
