package com.spring.aiprompt.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spring.aiprompt.common.Result;
import com.spring.aiprompt.entity.Prompt;
import com.spring.aiprompt.service.FavoriteService;
import com.spring.aiprompt.service.PromptService;
import com.spring.aiprompt.service.UserService;
import com.spring.aiprompt.vo.PromptVO;
import com.spring.aiprompt.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员接口：用户管理 / 全部Prompt / 统计 / 数据导出（全部需要 ADMIN 角色）
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
        return Result.success(userService.pageUsers(pageNum, pageSize));
    }

    /** 启用/禁用用户：status 1 正常 / 0 禁用 */
    @Operation(summary = "启用/禁用用户（管理员）")
    @PutMapping("/user/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateUserStatus(id, status);
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

    /**
     * 数据导出：把当前查询结果（可按关键词过滤）导出为 CSV 文件（带 UTF-8 BOM，Excel 直接打开不乱码）。
     * 属于数据转储子系统：查询结果转存为文件。
     */
    @Operation(summary = "导出Prompt查询结果为CSV（管理员）")
    @GetMapping("/prompt/export")
    public void exportPrompts(@RequestParam(required = false) String keyword,
                              HttpServletResponse response) throws IOException {
        List<PromptVO> records = promptService
                .pageList(1, 10000, keyword, null, null)
                .getRecords();

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String fileName = "Prompt数据_" + timestamp + ".csv";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"prompt_data_" + timestamp + ".csv\"; filename*=UTF-8''" + encoded);

        try (PrintWriter writer = response.getWriter()) {
            // BOM：让 Excel 识别为 UTF-8
            writer.write('\uFEFF');
            writer.println("ID,标题,内容,描述,分类,作者,浏览数,收藏数,发布时间");
            for (PromptVO p : records) {
                writer.println(String.join(",",
                        csv(p.getId()),
                        csv(p.getTitle()),
                        csv(p.getContent()),
                        csv(p.getDescription()),
                        csv(p.getCategoryName()),
                        csv(p.getUsername()),
                        csv(p.getViewCount()),
                        csv(p.getFavoriteCount()),
                        csv(p.getCreateTime())));
            }
            writer.flush();
        }
    }

    /** CSV 字段转义：含逗号/引号/换行的字段加双引号包裹，内部引号翻倍 */
    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
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
