package com.offerpilot.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerpilot.interview.domain.InterviewQuestion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InterviewQuestionMapper extends BaseMapper<InterviewQuestion> {
}
