package com.offerpilot.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerpilot.job.domain.JobTemplate;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JobTemplateMapper extends BaseMapper<JobTemplate> {
}
