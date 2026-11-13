package com.example.trainee.config;

import com.example.trainee.common.BizException;
import com.example.trainee.common.LoginUser;
import com.example.trainee.entity.UserSession;
import com.example.trainee.mapper.UserSessionMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 自定义 token 拦截器。
 * 1. 从 Authorization: Bearer xxx 取 token；
 * 2. 查 user_session 表，token 不存在返回 401；
 * 3. 把 LoginUser 写入 ThreadLocal 供 Service 层取用。
 *
 * 作业边界说明：不用 Spring Security 的完整过滤链，只用它提供 BCryptPasswordEncoder。
 * 拦截器覆盖所有业务接口，登录/登出/公共接口在 WebMvcConfig 里排除。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenInterceptor implements HandlerInterceptor {

    private final UserSessionMapper sessionMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"登录态无效，请重新登录\",\"data\":null}");
            return false;
        }
        String token = header.substring(7).trim();
        UserSession session = sessionMapper.findByToken(token);
        if (session == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"登录态已失效，请重新登录\",\"data\":null}");
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
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"无权访问该接口\",\"data\":null}");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    /** 只在拦截器内部持有，不暴露给业务层。 */
    public static class AuthContext {
        private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

        public static void set(LoginUser user) {
            HOLDER.set(user);
        }

        public static LoginUser get() {
            LoginUser u = HOLDER.get();
            if (u == null) throw new BizException(401, "登录态失效");
            return u;
        }

        public static LoginUser getOrNull() {
            return HOLDER.get();
        }

        public static void clear() {
            HOLDER.remove();
        }
    }
}
