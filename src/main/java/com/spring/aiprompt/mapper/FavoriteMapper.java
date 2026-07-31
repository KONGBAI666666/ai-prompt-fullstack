package com.spring.aiprompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spring.aiprompt.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收藏表 Mapper
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
