package com.example.trainee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.trainee.entity.Intention;
import com.example.trainee.entity.IntentionStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface IntentionMapper extends BaseMapper<Intention> {

    /**
     * 获取某学生全部意向，按 round 再 priority 升序（experience 2118733：排序字段明确）。
     */
    @Select("SELECT * FROM intention WHERE student_id = #{studentId} " +
            "ORDER BY round ASC, priority ASC")
    List<Intention> listByStudent(Long studentId);

    /**
     * 获取某导师收到的全部报考，可按状态过滤。
     */
    @Select({"<script>",
            "SELECT * FROM intention WHERE teacher_id = #{teacherId}",
            "<if test='status != null'> AND status = #{status}</if>",
            "ORDER BY submit_time ASC",
            "</script>"})
    List<Intention> listByTeacher(Long teacherId, IntentionStatus status);

    /**
     * 按实训室拿到递补顺位最小的一人。
     * 用于逾期放弃后自动递补（Service 层事务内调用）。
     */
    @Select("SELECT * FROM intention WHERE room_id = #{roomId} AND status = '递补' " +
            "ORDER BY waitlist_rank ASC LIMIT 1")
    Intention findNextWaitlist(Long roomId);

    /**
     * 查询"所有意向都结束（拟录取/已确认/已放弃/已录取/未录取），且没有任何拟录取/递补"的学生。
     * 用于管理员执行调剂前筛候选。
     */
    @Select("SELECT DISTINCT student_id FROM intention WHERE status IN ('未录取','已放弃') " +
            "AND student_id NOT IN (" +
            " SELECT student_id FROM intention WHERE status IN ('待面试','面试完成','拟录取','递补','已确认','已录取')" +
            ")")
    List<Long> findAllRejectedStudentIds();

    /**
     * 按 room_id + status 分组聚合。
     * 返回每行一个 {roomId, status, cnt}，Service 层归组为 Map<roomId, Map<status, cnt>>。
     * 用于 admissionsOverview 的 roomsView 聚合，替代 N+1 的 countOccupied + Java stream filter。
     */
    @Select("SELECT room_id AS roomId, status, COUNT(*) AS cnt " +
            "FROM intention WHERE room_id IS NOT NULL " +
            "GROUP BY room_id, status")
    List<Map<String, Object>> groupByRoomAndStatus();

    /**
     * 按 status 全局分组聚合。
     * 返回每行一个 {status, cnt}。用于 admissionsOverview 的 stats 部分，
     * 替代 Java 层对全表的 8 次 stream().filter().count()。
     */
    @Select("SELECT status, COUNT(*) AS cnt FROM intention GROUP BY status")
    List<Map<String, Object>> groupByStatus();
}
