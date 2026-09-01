package com.spring.aiprompt.service.impl;

import com.spring.aiprompt.entity.Permission;
import com.spring.aiprompt.mapper.PermissionMapper;
import com.spring.aiprompt.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;

    @Override
    public List<Permission> listAll() {
        return permissionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Permission>()
                        .orderByAsc("module", "code"));
    }
}