package com.spring.aiprompt.service;

import com.spring.aiprompt.entity.Permission;

import java.util.List;

/**
 * 权限点服务
 */
public interface PermissionService {

    /** 列出系统中定义的所有权限点（按模块、编码排序） */
    List<Permission> listAll();
}