package com.spring.aiprompt.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.spring.aiprompt.entity.Favorite;
import com.spring.aiprompt.vo.PromptVO;

/**
 * 收藏业务接口
 */
public interface FavoriteService extends IService<Favorite> {

    /** 收藏：查重 → 插入 → prompt.favorite_count 原子 +1 */
    void add(Long promptId);

    /** 取消收藏：删除 → prompt.favorite_count 原子 -1 */
    void cancel(Long promptId);

    /** 当前用户收藏的 Prompt 分页列表 */
    Page<PromptVO> myFavorites(long pageNum, long pageSize);
}
