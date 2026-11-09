package com.example.trainee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.trainee.entity.Interview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InterviewMapper extends BaseMapper<Interview> {
    @Select("SELECT * FROM interview WHERE intention_id = #{intentionId}")
    Interview findByIntention(Long intentionId);
}
