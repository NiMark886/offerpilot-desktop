package com.offerpilot.quota.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerpilot.quota.domain.UserQuota;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserQuotaMapper extends BaseMapper<UserQuota> {
}
