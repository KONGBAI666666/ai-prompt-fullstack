package com.spring.aiprompt.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器
 * <p>
 * 作用：当执行 INSERT 或 UPDATE 操作时，MyBatis-Plus 会回调这个类，
 * 自动给标注了 @TableField(fill = FieldFill.INSERT / INSERT_UPDATE) 的字段赋值。
 * <p>
 * 好处：业务代码里不用手动 setCreateTime / setUpdateTime，
 * 只要在实体类字段上标注 fill 策略，时间就会自动填上。
 * <p>
 * 使用方式：
 * - 实体类字段标注 @TableField(fill = FieldFill.INSERT) → 插入时自动填 createTime
 * - 实体类字段标注 @TableField(fill = FieldFill.INSERT_UPDATE) → 插入和更新都自动填 updateTime
 * <p>
 * strictInsertFill / strictUpdateFill：
 * "strict" 表示严格模式——只有当实体类字段标注了对应的 fill 策略时才会填充，
 * 不会往所有实体的所有字段都塞值。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时自动填充
     * <p>
     * 当执行 save() / insert() 操作时，MyBatis-Plus 回调此方法。
     * 给标注了 FieldFill.INSERT 的字段填充当前时间。
     *
     * @param metaObject MyBatis-Plus 的反射对象，封装了正在插入的实体
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        // 给 createTime 字段填充当前时间（只有标注了 FieldFill.INSERT 的实体才生效）
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        // 给 updateTime 字段也填充（INSERT_UPDATE 包含 INSERT 场景）
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        // 给 useTime 字段填充（只有 PromptHistory 实体的 useTime 标注了 INSERT）
        this.strictInsertFill(metaObject, "useTime", LocalDateTime.class, LocalDateTime.now());
    }

    /**
     * 更新时自动填充
     * <p>
     * 当执行 updateById() / lambdaUpdate() 操作时，MyBatis-Plus 回调此方法。
     * 给标注了 FieldFill.INSERT_UPDATE 的字段填充当前时间。
     *
     * @param metaObject MyBatis-Plus 的反射对象，封装了正在更新的实体
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        // 只有 updateTime 标注了 INSERT_UPDATE，所以只填这一个
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
