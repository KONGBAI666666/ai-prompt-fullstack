package com.spring.aiprompt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Objects;

/**
 * 角色-权限关联实体，对应表 role_permission（复合主键 role_code + permission_code）
 * 仅用作 MyBatis-Plus 的 BaseMapper&lt;RolePermission&gt; 载体，
 * 实际持久化在 RoleService 中通过 XML/注解 SQL 完成。
 * 注：复合主键无单一 @TableId，此处将 role_code 标记为 INPUT 型主键
 * 以消除 MyBatis-Plus 启动警告，代码中不使用 xxById 系列方法。
 */
@Data
@TableName("role_permission")
public class RolePermission implements Serializable {

    @TableId(value = "role_code", type = IdType.INPUT)
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