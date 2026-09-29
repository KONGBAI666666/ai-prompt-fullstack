package com.spring.aiprompt.vo;

import lombok.Data;

/**
 * 分类统计视图的行对象，对应数据库视图 v_category_stat
 * <p>
 * 视图定义（见 init.sql）：按分类聚合出该分类下的 Prompt 数量、总浏览量、总收藏数。
 * 用视图而不是在 Java 里写三张表的聚合查询，好处是：
 * 聚合口径只定义一次，管理后台、报表、存储过程都读同一个视图，不会出现"两处算法不一致"。
 * <p>
 * 字段类型说明：视图里的 SUM() 在 MySQL 中返回 DECIMAL，
 * Mapper 的 SQL 里用 CAST(... AS SIGNED) 转成整数再映射到这里的 Long，避免映射歧义。
 */
@Data
public class CategoryStatVO {

    /** 分类 id */
    private Long categoryId;

    /** 分类名称 */
    private String categoryName;

    /** 该分类下的 Prompt 数量 */
    private Long promptCount;

    /** 该分类下所有 Prompt 的浏览量之和 */
    private Long totalViewCount;

    /** 该分类下所有 Prompt 的收藏数之和 */
    private Long totalFavoriteCount;
}
