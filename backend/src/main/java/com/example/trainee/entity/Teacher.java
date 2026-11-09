package com.example.trainee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("teacher")
public class Teacher {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String teacherNo;
    private String name;
    private String title;
    private Long roomId;
    @TableField("password_hash")
    private String passwordHash;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
