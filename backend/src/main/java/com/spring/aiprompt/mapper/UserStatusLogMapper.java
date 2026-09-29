package com.spring.aiprompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spring.aiprompt.entity.UserStatusLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户状态变更审计日志 Mapper —— 数据访问层接口
 * <p>
 * 只有读操作：数据由数据库触发器 trg_user_status_change 写入，应用不负责插入/修改/删除。
 * 因此这里没有自定义方法，BaseMapper 自带的 selectPage / selectList 就够了。
 */
@Mapper
public interface UserStatusLogMapper extends BaseMapper<UserStatusLog> {
}
