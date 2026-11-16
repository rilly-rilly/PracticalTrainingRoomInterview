package com.example.trainee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.trainee.common.BizException;
import com.example.trainee.common.LoginUser;
import com.example.trainee.config.TokenInterceptor;
import com.example.trainee.entity.AdminUser;
import com.example.trainee.entity.Student;
import com.example.trainee.entity.Teacher;
import com.example.trainee.entity.TrainingRoom;
import com.example.trainee.entity.UserSession;
import com.example.trainee.mapper.AdminUserMapper;
import com.example.trainee.mapper.StudentMapper;
import com.example.trainee.mapper.TeacherMapper;
import com.example.trainee.mapper.TrainingRoomMapper;
import com.example.trainee.mapper.UserSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 登录/登出/当前用户信息。
 *
 * 账号路由规则：
 * - username == "admin"       -> 管理员表
 * - username 以 T/t 开头     -> 导师表，按 teacherNo 匹配
 * - 其他                     -> 学生表，按 studentNo 匹配
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AdminUserMapper adminUserMapper;
    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;
    private final TrainingRoomMapper trainingRoomMapper;
    private final UserSessionMapper sessionMapper;
    private final PasswordEncoder encoder;

    /**
     * 登录，返回 { token, role, profile }。
     *
     * 三张表并行匹配，不再靠字符串前缀猜测角色：
     * - admin 表：username = "admin"
     * - teacher 表：teacher_no = username
     * - student 表：student_no = username
     * 任一张表密码匹配即返回对应角色，避免学生学号如 T2026001 被误判为导师。
     */
    public Map<String, Object> login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BizException(400, "账号或密码不能为空");
        }

        // 同时查三张表，密码匹配第一个即返回
        // admin
        AdminUser admin = "admin".equals(username) ? adminUserMapper.findByUsername(username) : null;
        if (admin != null && encoder.matches(password, admin.getPasswordHash())) {
            return successLogin("admin", admin.getId(), admin.getName(),
                    adminUserMapper, teacherMapper, studentMapper, username, admin);
        }

        // teacher
        Teacher teacher = teacherMapper.findByNo(username);
        if (teacher != null && encoder.matches(password, teacher.getPasswordHash())) {
            return successLogin("teacher", teacher.getId(), teacher.getName(),
                    adminUserMapper, teacherMapper, studentMapper, username, teacher);
        }

        // student
        Student student = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, username));
        if (student != null && encoder.matches(password, student.getPasswordHash())) {
            return successLogin("student", student.getId(), student.getName(),
                    adminUserMapper, teacherMapper, studentMapper, username, student);
        }

        throw new BizException(401, "账号或密码错误");
    }

    /** 登录成功后共用的 token + session + 返回构建逻辑 */
    private Map<String, Object> successLogin(
            String role, Long userId, String name,
            AdminUserMapper adminUserMapper, TeacherMapper teacherMapper, StudentMapper studentMapper,
            String username, Object matchedEntity) {
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        UserSession session = new UserSession();
        session.setToken(token);
        session.setRole(role);
        session.setUserId(userId);
        session.setCreateTime(now);
        // 会话默认 7 天自动过期，过期后拦截器返回 401
        session.setExpireAt(now.plusDays(7));
        sessionMapper.insert(session);

        log.info("用户登录成功: role={}, userId={}, name={}", role, userId, name);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("role", role);

        Map<String, Object> profile = switch (role) {
            case "admin" -> buildAdminProfile((AdminUser) matchedEntity);
            case "teacher" -> buildTeacherProfile((Teacher) matchedEntity);
            case "student" -> buildStudentProfile((Student) matchedEntity);
            default -> throw new BizException(500, "未知角色");
        };
        result.put("profile", profile);
        return result;
    }

    /** 登出 */
    public void logout() {
        LoginUser user = TokenInterceptor.AuthContext.getOrNull();
        if (user == null) return;
        sessionMapper.delete(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getToken, user.getToken()));
    }

    /** 当前登录用户资料 */
    public Map<String, Object> me() {
        LoginUser user = TokenInterceptor.AuthContext.get();
        return switch (user.getRole()) {
            case "admin" -> {
                AdminUser admin = adminUserMapper.selectById(user.getUserId());
                if (admin == null) throw new BizException(404, "用户不存在");
                yield buildAdminProfile(admin);
            }
            case "teacher" -> {
                Teacher t = teacherMapper.selectById(user.getUserId());
                if (t == null) throw new BizException(404, "用户不存在");
                yield buildTeacherProfile(t);
            }
            case "student" -> {
                Student s = studentMapper.selectById(user.getUserId());
                if (s == null) throw new BizException(404, "用户不存在");
                yield buildStudentProfile(s);
            }
            default -> throw new BizException(401, "未知角色");
        };
    }

    // ---------- profile 构建 ----------

    private Map<String, Object> buildAdminProfile(AdminUser a) {
        Map<String, Object> p = new HashMap<>();
        p.put("id", a.getId());
        p.put("role", "admin");
        p.put("name", a.getName());
        p.put("username", a.getUsername());
        return p;
    }

    private Map<String, Object> buildTeacherProfile(Teacher t) {
        Map<String, Object> p = new HashMap<>();
        p.put("id", t.getId());
        p.put("role", "teacher");
        p.put("name", t.getName());
        p.put("no", t.getTeacherNo());
        p.put("title", t.getTitle());
        p.put("roomId", t.getRoomId());
        TrainingRoom room = t.getRoomId() != null ? trainingRoomMapper.selectById(t.getRoomId()) : null;
        p.put("roomName", room != null ? room.getName() : null);
        return p;
    }

    private Map<String, Object> buildStudentProfile(Student s) {
        Map<String, Object> p = new HashMap<>();
        p.put("id", s.getId());
        p.put("role", "student");
        p.put("name", s.getName());
        p.put("no", s.getStudentNo());
        p.put("major", s.getMajor());
        p.put("grade", s.getGrade());
        return p;
    }
}
