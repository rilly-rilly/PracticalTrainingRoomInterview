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

    /** 登录，返回 { token, role, profile } */
    public Map<String, Object> login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BizException(400, "账号或密码不能为空");
        }

        String role;
        Long userId;
        String name;
        Map<String, Object> profile;

        // 1) 路由账号
        if ("admin".equals(username)) {
            AdminUser admin = adminUserMapper.findByUsername(username);
            if (admin == null || !encoder.matches(password, admin.getPasswordHash())) {
                throw new BizException(401, "账号或密码错误");
            }
            role = "admin";
            userId = admin.getId();
            name = admin.getName();
            profile = buildAdminProfile(admin);
        } else if (username.matches("^(?i)T.*")) {
            Teacher teacher = teacherMapper.findByNo(username);
            if (teacher == null || !encoder.matches(password, teacher.getPasswordHash())) {
                throw new BizException(401, "账号或密码错误");
            }
            role = "teacher";
            userId = teacher.getId();
            name = teacher.getName();
            profile = buildTeacherProfile(teacher);
        } else {
            Student student = studentMapper.selectOne(
                    new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, username));
            if (student == null || !encoder.matches(password, student.getPasswordHash())) {
                throw new BizException(401, "账号或密码错误");
            }
            role = "student";
            userId = student.getId();
            name = student.getName();
            profile = buildStudentProfile(student);
        }

        // 2) 生成 token，写 session
        String token = UUID.randomUUID().toString().replace("-", "");
        UserSession session = new UserSession();
        session.setToken(token);
        session.setRole(role);
        session.setUserId(userId);
        session.setCreateTime(LocalDateTime.now());
        sessionMapper.insert(session);

        log.info("用户登录成功: role={}, userId={}, name={}", role, userId, name);

        // 3) 返回
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("role", role);
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
