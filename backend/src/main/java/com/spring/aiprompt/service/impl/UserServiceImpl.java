package com.spring.aiprompt.service.impl;

import cn.dev33.satoken.stp.StpUtil;
// MyBatis-Plus 3.5.10+ 中 ServiceImpl 已迁移至 spring 模块包
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.spring.aiprompt.dto.LoginDTO;
import com.spring.aiprompt.dto.RegisterDTO;
import com.spring.aiprompt.entity.User;
import com.spring.aiprompt.exception.BusinessException;
import com.spring.aiprompt.mapper.UserMapper;
import com.spring.aiprompt.service.CaptchaService;
import com.spring.aiprompt.service.UserService;
import com.spring.aiprompt.vo.LoginVO;
import com.spring.aiprompt.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户业务实现
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final BCryptPasswordEncoder passwordEncoder;
    private final CaptchaService captchaService;

    @Override
    public void register(RegisterDTO dto) {
        // 用户名查重（数据库唯一索引 uk_username 兜底并发场景）
        boolean exists = lambdaQuery().eq(User::getUsername, dto.getUsername()).exists();
        if (exists) {
            throw new BusinessException("用户名已存在");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setEmail(dto.getEmail());
        user.setRole("USER");
        user.setStatus(1);
        try {
            save(user);
        } catch (DuplicateKeyException e) {
            // 并发下绕过上方查重时，由唯一索引兜底并转为友好提示
            throw new BusinessException("用户名已存在");
        }
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        // 第一道关：图形验证码（一次性），拦截自动化撞库
        if (!captchaService.verifyAndConsume(dto.getCaptchaId(), dto.getCaptchaCode())) {
            throw new BusinessException("验证码错误或已过期");
        }
        User user = lambdaQuery().eq(User::getUsername, dto.getUsername()).one();
        // 用户不存在与密码错误统一提示，避免暴露用户名是否已注册
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用");
        }
        StpUtil.login(user.getId());
        LoginVO vo = new LoginVO();
        vo.setToken(StpUtil.getTokenValue());
        vo.setUser(toVO(user));
        return vo;
    }

    @Override
    public UserVO getCurrentUser() {
        User user = getById(StpUtil.getLoginIdAsLong());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return toVO(user);
    }

    @Override
    public Page<UserVO> pageUsers(long pageNum, long pageSize) {
        Page<User> page = lambdaQuery()
                .orderByDesc(User::getCreateTime)
                .page(new Page<>(pageNum, pageSize));
        Page<UserVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    @Override
    public void updateUserStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("status只能为0或1");
        }
        User user = getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if ("ADMIN".equals(user.getRole())) {
            throw new BusinessException("不能禁用管理员账号");
        }
        user.setStatus(status);
        updateById(user);
        // 禁用后立即踢下线，否则已持有的 token 在过期前仍可正常访问
        if (status == 0) {
            StpUtil.kickout(id);
        }
    }

    @Override
    public UserVO toVO(User user) {
        // 按字段名复制，UserVO 无 password 字段，密码天然不出网
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
