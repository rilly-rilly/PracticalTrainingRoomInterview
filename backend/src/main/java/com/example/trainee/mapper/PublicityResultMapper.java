package com.example.trainee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.trainee.entity.PublicityResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PublicityResultMapper extends BaseMapper<PublicityResult> {
    @Select("SELECT * FROM publicity_result WHERE student_id = #{studentId}")
    PublicityResult findByStudent(Long studentId);
}
