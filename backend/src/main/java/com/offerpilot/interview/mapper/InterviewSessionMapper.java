package com.offerpilot.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerpilot.interview.domain.InterviewSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InterviewSessionMapper extends BaseMapper<InterviewSession> {
}
