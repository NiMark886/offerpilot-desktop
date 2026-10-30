package com.offerpilot.track.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerpilot.track.domain.TrackQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TrackQuestionMapper extends BaseMapper<TrackQuestion> {

    @Select("""
            SELECT COUNT(*)
            FROM track_question tq
            JOIN question_bank q ON q.id = tq.question_id
            WHERE tq.track_id = #{trackId}
              AND tq.enabled = 1
              AND q.status = 'ACTIVE'
              AND q.deleted = 0
            """)
    long countActiveQuestions(@Param("trackId") Long trackId);
}
