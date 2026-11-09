package com.example.trainee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.trainee.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {
    @Select("SELECT * FROM admin_user WHERE username = #{username}")
    AdminUser findByUsername(String username);
}
