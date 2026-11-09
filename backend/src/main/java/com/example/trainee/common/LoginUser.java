package com.example.trainee.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 拦截器解析出来的登录态，Controller 不感知，Service 层按需取用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {
    private String role; // student / teacher / admin
    private Long userId; // student.id / teacher.id / admin.id
    private String token;
}
