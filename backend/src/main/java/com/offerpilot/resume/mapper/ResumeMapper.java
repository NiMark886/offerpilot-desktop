package com.offerpilot.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerpilot.resume.domain.Resume;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ResumeMapper extends BaseMapper<Resume> {
}
