package com.example.trainee.config;

import com.example.trainee.common.ApiResponse;
import com.example.trainee.common.BizException;
import com.example.trainee.common.LoginUser;
import com.example.trainee.entity.UserSession;
import com.example.trainee.mapper.UserSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

/**
 * 自定义 token 拦截器。
 * 1. 从 Authorization: Bearer xxx 取 token；
 * 2. 查 user_session 表，token 不存在返回 401；expire_at 过期返回 401；
 * 3. 把 LoginUser 写入 ThreadLocal 供 Service 层取用；
 * 4. 路径级角色守卫：/api/student/**、/api/teacher/**、/api/admin/** 互不越权。
 *
 * 作业边界说明：不用 Spring Security 的完整过滤链，只用它提供 BCryptPasswordEncoder。
 * 拦截器覆盖所有业务接口，登录/登出/公共接口在 WebMvcConfig 里排除。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenInterceptor implements HandlerInterceptor {

    private final UserSessionMapper sessionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeError(response, 401, "登录态无效，请重新登录");
            return false;
        }
        String token = header.substring(7).trim();
        UserSession session = sessionMapper.findByToken(token);
        if (session == null) {
            writeError(response, 401, "登录态已失效，请重新登录");
            return false;
        }

        // token 过期检查：expire_at <= NOW() 视为失效
        if (session.getExpireAt() != null && !session.getExpireAt().isAfter(LocalDateTime.now())) {
            writeError(response, 401, "登录已过期，请重新登录");
            return false;
        }

        AuthContext.set(new LoginUser(session.getRole(), session.getUserId(), token));

        // 路径级角色守卫：学生/导师/管理员接口互不越权
        String path = request.getRequestURI();
        String role = session.getRole();
        boolean forbidden = (path.startsWith("/api/student/") && !"student".equals(role))
                || (path.startsWith("/api/teacher/") && !"teacher".equals(role))
                || (path.startsWith("/api/admin/") && !"admin".equals(role));
        if (forbidden) {
            AuthContext.clear();
            writeError(response, 403, "无权访问该接口");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    /**
     * 拦截器内统一写 JSON 错误响应。
     * 之前三处硬编码 {@code "{\"code\":401,...}"} 同步维护困难，
     * 现在用 ObjectMapper 序列化 {@link ApiResponse}，和全局异常处理器保持一致。
     */
    private void writeError(HttpServletResponse response, int httpStatus, String message) {
        try {
            response.setStatus(httpStatus);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(httpStatus, message)));
        } catch (Exception ignored) {
            // 序列化失败兜底写一行纯文本
            response.setStatus(httpStatus);
            response.setContentType("text/plain;charset=UTF-8");
            try { response.getWriter().write(message); } catch (Exception ignoreNested) { /* NOP */ }
        }
    }

    /** 只在拦截器内部持有，不暴露给业务层。 */
    public static class AuthContext {
        private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

        public static void set(LoginUser user) { HOLDER.set(user); }

        public static LoginUser get() {
            LoginUser u = HOLDER.get();
            if (u == null) throw new BizException(401, "登录态失效");
            return u;
        }

        public static LoginUser getOrNull() { return HOLDER.get(); }

        public static void clear() { HOLDER.remove(); }
    }
}
