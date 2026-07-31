package com.spring.aiprompt.service.impl;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.util.StrUtil;
import com.spring.aiprompt.entity.User;
import com.spring.aiprompt.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 角色/权限数据源：@SaCheckRole 校验时回调这里
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final UserMapper userMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 第一版只做角色鉴权，不做细粒度权限
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        User user = userMapper.selectById(Long.valueOf(loginId.toString()));
        if (user == null || StrUtil.isBlank(user.getRole())) {
            return Collections.emptyList();
        }
        return List.of(user.getRole());
    }
}
