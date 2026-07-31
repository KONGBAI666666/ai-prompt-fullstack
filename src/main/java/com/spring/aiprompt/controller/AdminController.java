package com.spring.aiprompt.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spring.aiprompt.common.Result;
import com.spring.aiprompt.entity.Prompt;
import com.spring.aiprompt.entity.User;
import com.spring.aiprompt.exception.BusinessException;
import com.spring.aiprompt.service.FavoriteService;
import com.spring.aiprompt.service.PromptService;
import com.spring.aiprompt.service.UserService;
import com.spring.aiprompt.vo.PromptVO;
import com.spring.aiprompt.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理员接口：用户管理 / 全部Prompt / 统计（全部需要 ADMIN 角色）
 */
@Tag(name = "管理员")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@SaCheckRole("ADMIN")
public class AdminController {

    private final UserService userService;
    private final PromptService promptService;
    private final FavoriteService favoriteService;

    /** 用户分页列表（UserVO 不含密码） */
    @Operation(summary = "用户分页列表（管理员）")
    @GetMapping("/user/list")
    public Result<Page<UserVO>> userList(@RequestParam(defaultValue = "1") long pageNum,
                                         @RequestParam(defaultValue = "10") long pageSize) {
        Page<User> page = userService.lambdaQuery()
                .orderByDesc(User::getCreateTime)
                .page(new Page<>(pageNum, pageSize));
        Page<UserVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(userService::toVO).toList());
        return Result.success(voPage);
    }

    /** 启用/禁用用户：status 1 正常 / 0 禁用 */
    @Operation(summary = "启用/禁用用户（管理员）")
    @PutMapping("/user/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("status只能为0或1");
        }
        User user = userService.getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if ("ADMIN".equals(user.getRole())) {
            throw new BusinessException("不能禁用管理员账号");
        }
        user.setStatus(status);
        userService.updateById(user);
        // 禁用后立即踢下线，否则已持有的 token 在过期前仍可正常访问
        if (status == 0) {
            StpUtil.kickout(id);
        }
        return Result.success();
    }

    /** 所有 Prompt 分页（管理员视角，支持关键词） */
    @Operation(summary = "所有Prompt分页列表（管理员）")
    @GetMapping("/prompt/list")
    public Result<Page<PromptVO>> promptList(@RequestParam(defaultValue = "1") long pageNum,
                                             @RequestParam(defaultValue = "10") long pageSize,
                                             @RequestParam(required = false) String keyword) {
        return Result.success(promptService.pageList(pageNum, pageSize, keyword, null, null));
    }

    /** 统计：用户总数 / Prompt总数 / 收藏总数 / 今日新增Prompt数 */
    @Operation(summary = "系统统计（管理员）")
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("userCount", userService.count());
        stats.put("promptCount", promptService.count());
        stats.put("favoriteCount", favoriteService.count());
        // 今日新增：create_time >= 今天 00:00
        stats.put("todayPromptCount", promptService.lambdaQuery()
                .ge(Prompt::getCreateTime, LocalDate.now().atStartOfDay())
                .count());
        return Result.success(stats);
    }
}
