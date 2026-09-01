package com.spring.aiprompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spring.aiprompt.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收藏表 Mapper —— 数据访问层接口
 * 继承 BaseMapper&lt;Favorite&gt; 自动拥有增删改查能力。
 * 用于 FavoriteServiceImpl 收藏/取消收藏，以及 PromptServiceImpl 中批量查"是否已收藏"。
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
