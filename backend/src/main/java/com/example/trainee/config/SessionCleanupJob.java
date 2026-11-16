package com.example.trainee.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.trainee.entity.UserSession;
import com.example.trainee.mapper.UserSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 定时任务：清理过期的 user_session 行。
 * 拦截器里 expire_at 过期的 token 已经被拒绝，这步只是物理清理，
 * 防止 user_session 表无限增长。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionCleanupJob {

    private final UserSessionMapper sessionMapper;

    /** 每天凌晨 4 点跑一次（业务低峰） */
    @Scheduled(cron = "0 0 4 * * ?")
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now();
        // DELETE FROM user_session WHERE expire_at < #{now}
        int removed = sessionMapper.delete(
                new LambdaQueryWrapper<UserSession>()
                        .lt(UserSession::getExpireAt, now));
        if (removed > 0) {
            log.info("[session-cleanup] 清理过期会话 {} 条，now={}", removed, now);
        }
    }
}
