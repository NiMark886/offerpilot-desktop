package com.offerpilot.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerpilot.interview.domain.InterviewMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InterviewMessageMapper extends BaseMapper<InterviewMessage> {
}
