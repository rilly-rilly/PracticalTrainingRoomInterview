package com.example.trainee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_session")
public class UserSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String token;
    private String role;
    private Long userId;
    private LocalDateTime createTime;
    /** 会话过期时间（默认登录后 7 天），拦截器 preHandle 里会校验 */
    private LocalDateTime expireAt;
}
