package com.offerpilot.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerpilot.user.domain.RefreshToken;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {
}
