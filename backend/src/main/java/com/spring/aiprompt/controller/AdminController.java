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
 * 管理员接口 Controller —— 整个类加 @SaCheckRole("ADMIN")
 * <p>
 * 路径前缀：/admin
 * 类级别 @SaCheckRole("ADMIN")：这个类里的所有方法都需要 ADMIN 角色，
 * 非管理员访问任何 /admin/xxx 接口都会返回 403。
 * <p>
 * 功能：
 * 1. 用户管理：分页查用户列表、启用/禁用用户
 * 2. 内容管理：分页查所有 Prompt（可按关键词搜索）
 * 3. 数据导出：把查询结果导出为 CSV 文件（数据转储子系统）
 * 4. 统计：用户总数、Prompt 总数、收藏总数、今日新增数
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

    /**
     * 用户分页列表（管理员）
     * <p>
     * 返回 UserVO（不含 password），管理员在后台"用户管理"Tab 展示。
     * 排序：按注册时间倒序（最新用户排前面）。
     */
    @Operation(summary = "用户分页列表（管理员）")
    @GetMapping("/user/list")
    public Result<Page<UserVO>> userList(@RequestParam(defaultValue = "1") long pageNum,
                                         @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(userService.pageUsers(pageNum, pageSize));
    }

    /**
     * 启用/禁用用户（管理员）
     * <p>
     * status=1 正常，status=0 禁用。
     * 禁用后立即踢下线（StpUtil.kickout），触发器自动写审计日志。
     */
    @Operation(summary = "启用/禁用用户（管理员）")
    @PutMapping("/user/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateUserStatus(id, status);
        return Result.success();
    }

    /**
     * 所有 Prompt 分页（管理员视角，支持关键词搜索）
     * <p>
     * 和首页列表的区别：管理员能看到所有人的 Prompt（首页也能看到，但管理员还要能管理/删除）。
     * 复用 promptService.pageList，onlyUserId 传 null 表示不限用户。
     */
    @Operation(summary = "所有Prompt分页列表（管理员）")
    @GetMapping("/prompt/list")
    public Result<Page<PromptVO>> promptList(@RequestParam(defaultValue = "1") long pageNum,
                                             @RequestParam(defaultValue = "10") long pageSize,
                                             @RequestParam(required = false) String keyword) {
        return Result.success(promptService.pageList(pageNum, pageSize, keyword, null, null));
    }

    /**
     * 数据导出：把当前查询结果（可按关键词过滤）导出为 CSV 文件
     * <p>
     * 属于评分表里的"数据转储"加分项。
     * <p>
     * 技术细节：
     * 1. 带 UTF-8 BOM（\uFEFF）：让 Excel 正确识别 UTF-8 编码，中文不乱码
     * 2. 文件名含中文：用 RFC 5987 的 filename*=UTF-8'' 前缀做 URL 编码
     * 3. CSV 字段转义：含逗号/引号/换行的字段加双引号包裹，内部引号翻倍
     * 4. 直接写 HttpServletResponse 的输出流，不经过 Result 包装（返回类型是 void）
     *
     * @param keyword   关键词（可选，尊重搜索条件）
     * @param response  Servlet 响应对象，直接往里写 CSV 内容
     */
    @Operation(summary = "导出Prompt查询结果为CSV（管理员）")
    @GetMapping("/prompt/export")
    public void exportPrompts(@RequestParam(required = false) String keyword,
                              HttpServletResponse response) throws IOException {
        // 查出所有符合条件的记录（pageSize 设大值一次取完）
        List<PromptVO> records = promptService
                .pageList(1, 10000, keyword, null, null)
                .getRecords();

        // 生成文件名：Prompt数据_20260901161500.csv
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String fileName = "Prompt数据_" + timestamp + ".csv";
        // URL 编码文件名（中文需编码后才能放在 HTTP 头里）
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        // 设置响应头
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        // Content-Disposition: attachment → 浏览器弹下载框
        // filename= → 兜底（不支持 RFC 5987 的旧浏览器）
        // filename*=UTF-8'' → RFC 5987 标准，支持中文文件名
        response.setHeader("Content-Disposition",
                "attachment; filename=\"prompt_data_" + timestamp + ".csv\"; filename*=UTF-8''" + encoded);

        try (PrintWriter writer = response.getWriter()) {
            // BOM：写一个 \uFEFF 字符，让 Excel 识别为 UTF-8
            // 没有 BOM 的纯 UTF-8 CSV，Excel 会按 GBK 解析，中文全乱码
            writer.write('\uFEFF');
            // 表头
            writer.println("ID,标题,内容,描述,分类,作者,浏览数,收藏数,发布时间");
            // 数据行
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

    /**
     * CSV 字段转义
     * <p>
     * CSV 规范：如果字段值包含逗号、引号、或换行符，需要用双引号包裹整个字段，
     * 字段内部的引号要翻倍（" → ""）。
     * 例如：字段值 He said "Hi", then left → "He said ""Hi"", then left"
     *
     * @param value 原始值
     * @return 转义后的 CSV 字段
     */
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

    /**
     * 系统统计（管理员）
     * <p>
     * 返回四项数据：
     * - userCount：用户总数
     * - promptCount：Prompt 总数
     * - favoriteCount：收藏总数
     * - todayPromptCount：今日新增 Prompt 数
     * <p>
     * count()：MyBatis-Plus 基类方法，等价于 SELECT COUNT(*) FROM 表
     * lambdaQuery().ge(...).count()：带条件的计数
     *
     * @return Map<统计项名, 数值>
     */
    @Operation(summary = "系统统计（管理员）")
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        // LinkedHashMap 保持插入顺序（JSON 输出顺序固定）
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("userCount", userService.count());
        stats.put("promptCount", promptService.count());
        stats.put("favoriteCount", favoriteService.count());
        // 今日新增：create_time >= 今天 00:00:00
        // LocalDate.now().atStartOfDay() → 今天 00:00:00
        stats.put("todayPromptCount", promptService.lambdaQuery()
                .ge(Prompt::getCreateTime, LocalDate.now().atStartOfDay())
                .count());
        return Result.success(stats);
    }
}
