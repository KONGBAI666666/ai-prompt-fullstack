package com.spring.aiprompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spring.aiprompt.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper —— 数据访问层接口
 * <p>
 * 继承 BaseMapper&lt;User&gt; 后自动拥有以下方法（无需手写任何 SQL）：
 * - insert(entity)：插入 → INSERT INTO sys_user (...) VALUES (...)
 * - deleteById(id)：按主键删除 → DELETE FROM sys_user WHERE id = ?
 * - updateById(entity)：按主键更新 → UPDATE sys_user SET ... WHERE id = ?
 * - selectById(id)：按主键查询 → SELECT * FROM sys_user WHERE id = ?
 * - selectList(wrapper)：条件查询 → SELECT * FROM sys_user WHERE ...
 * - selectBatchIds(ids)：批量查询 → SELECT * FROM sys_user WHERE id IN (?, ...)
 * - selectCount(wrapper)：条件计数 → SELECT COUNT(*) FROM sys_user WHERE ...
 * <p>
 * @Mapper：MyBatis 注解，标记这是一个 Mapper 接口，Spring 会自动创建代理实现类。
 * 因为启动类上有 @MapperScan，其实不加 @Mapper 也行，但加上更明确。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
