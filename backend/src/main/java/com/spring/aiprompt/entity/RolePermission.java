package com.spring.aiprompt.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Objects;

/**
 * 角色-权限关联实体，对应表 role_permission（复合主键）
 * 仅用作 MyBatis-Plus 的 BaseMapper<RolePermission> 载体，
 * 实际持久化在 RoleService 中通过 XML/注解 SQL 完成
 */
@Data
@TableName("role_permission")
public class RolePermission implements Serializable {

    private String roleCode;

    private String permissionCode;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RolePermission that)) return false;
        return Objects.equals(roleCode, that.roleCode)
            && Objects.equals(permissionCode, that.permissionCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleCode, permissionCode);
    }
}