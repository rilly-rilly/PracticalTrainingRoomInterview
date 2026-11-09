package com.example.trainee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.trainee.entity.Teacher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TeacherMapper extends BaseMapper<Teacher> {
    @Select("SELECT * FROM teacher WHERE teacher_no = #{no}")
    Teacher findByNo(String no);
}
