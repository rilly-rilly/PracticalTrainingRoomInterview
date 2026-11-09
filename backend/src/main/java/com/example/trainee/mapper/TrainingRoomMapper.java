package com.example.trainee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.trainee.entity.TrainingRoom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TrainingRoomMapper extends BaseMapper<TrainingRoom> {

    /**
     * 按实训室编码查询，用于导师导入时匹配。
     */
    @Select("SELECT * FROM training_room WHERE room_code = #{code}")
    TrainingRoom findByCode(String code);

    /**
     * 统计当前实训室已占用名额。
     * 仅拟录取/已确认/已录取算占用（与 mock server.js 的 OCCUPIED 一致）。
     * 不要用 id desc 代替 create_time desc（experience 2118733 教训）。
     */
    @Select("SELECT COUNT(*) FROM intention " +
            "WHERE room_id = #{roomId} AND status IN ('拟录取','已确认','已录取')")
    int countOccupied(Long roomId);
}
