package com.spring.aiprompt.service;

// MyBatis-Plus 3.5.10+ 中 IService 已迁移至 spring 模块包
import com.baomidou.mybatisplus.spring.service.IService;
import com.spring.aiprompt.dto.LoginDTO;
import com.spring.aiprompt.dto.RegisterDTO;
import com.spring.aiprompt.entity.User;
import com.spring.aiprompt.vo.LoginVO;
import com.spring.aiprompt.vo.UserVO;

/**
 * 用户业务接口
 */
public interface UserService extends IService<User> {

    /** 注册：用户名查重 → BCrypt 加密 → 入库 */
    void register(RegisterDTO dto);

    /** 登录：校验密码与状态 → Sa-Token 登录 → 返回 token + 用户信息 */
    LoginVO login(LoginDTO dto);

    /** 当前登录用户信息（不含密码） */
    UserVO getCurrentUser();

    /** User 转 UserVO（脱敏，供管理员用户列表复用） */
    UserVO toVO(User user);
}
