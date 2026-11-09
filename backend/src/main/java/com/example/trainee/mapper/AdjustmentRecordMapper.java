package com.example.trainee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.trainee.entity.AdjustmentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdjustmentRecordMapper extends BaseMapper<AdjustmentRecord> {
    @Select("SELECT * FROM adjustment_record WHERE student_id = #{studentId} ORDER BY create_time DESC")
    List<AdjustmentRecord> listByStudent(Long studentId);
}
