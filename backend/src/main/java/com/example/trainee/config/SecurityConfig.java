package com.example.trainee.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 仅注册 BCryptPasswordEncoder Bean，并禁用 Spring Security 默认过滤链。
 * 完整认证链路由自定义 TokenInterceptor 提供，符合作业"不做 OAuth2/SSO"的边界。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（REST API 无状态）
            .csrf(AbstractHttpConfigurer::disable)
            // 禁用默认登录表单与 HTTP Basic
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            // 禁用 Session（无状态）
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 所有请求放行，认证由 TokenInterceptor 处理
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            // 禁用默认 UserDetailsService（我们不用 Spring Security 认证）
            .userDetailsService(username -> {
                throw new UnsupportedOperationException("认证由 TokenInterceptor 处理");
            });
        return http.build();
    }
}
