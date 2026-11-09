package com.example.trainee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.trainee.entity.UserSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserSessionMapper extends BaseMapper<UserSession> {
    @Select("SELECT * FROM user_session WHERE token = #{token}")
    UserSession findByToken(String token);
}
