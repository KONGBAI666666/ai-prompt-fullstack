package com.spring.aiprompt.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.spring.aiprompt.common.Result;
import com.spring.aiprompt.dto.LoginDTO;
import com.spring.aiprompt.dto.RegisterDTO;
import com.spring.aiprompt.service.UserService;
import com.spring.aiprompt.vo.LoginVO;
import com.spring.aiprompt.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口：注册 / 登录 / 当前用户信息 / 退出登录
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 注册（公开接口） */
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Validated @RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.success();
    }

    /** 登录（公开接口），返回 token + 用户信息 */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Validated @RequestBody LoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    /** 当前登录用户信息 */
    @Operation(summary = "当前登录用户信息")
    @GetMapping("/info")
    public Result<UserVO> info() {
        return Result.success(userService.getCurrentUser());
    }

    /** 退出登录 */
    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.success();
    }
}
