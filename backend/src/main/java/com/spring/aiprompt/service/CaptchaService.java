package com.spring.aiprompt.service;

import com.spring.aiprompt.vo.CaptchaVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 图形验证码服务 —— 防止机器人暴力撞库登录的安全组件
 * <p>
 * 整体流程：
 * 1. 前端打开登录页 → 调 GET /user/captcha → 后端 generate() 生成验证码图 → 返回 {id, base64图片}
 * 2. 前端把图片显示在 img 标签里，用户人眼识别验证码
 * 3. 用户提交登录 → 带上 captchaId + captchaCode → 后端 verifyAndConsume() 校验
 * 4. 校验通过 → 验证码从 Redis 中删除（一次性）→ 继续走登录流程
 * 5. 校验失败 → 返回"验证码错误"→ 前端自动刷新一张新图
 * <p>
 * 技术选型：
 * - 生成：用 Java AWT（Graphics2D）手绘图片，不依赖任何第三方图形库
 * - 存储：Redis（SET key code EX 300）—— 验证码是典型的"短生命周期 + 需要跨实例共享"数据，
 *   存数据库太重，存 JVM 内存（ConcurrentHashMap）在多实例部署时各节点验证码互不可见、
 *   且重启即全部失效。Redis 的 EX 过期天然替代了手写的惰性清理逻辑。
 * - 过期：TTL 5 分钟，由 Redis 自动删除，不再需要 evictExpired() 清理线程/清理遍历
 * - 安全：SecureRandom（密码学安全随机数生成器），比普通 Random 更难预测
 * - 一次性：verifyAndConsume 用 Lua 脚本原子地"取出并删除"（GET+DEL），
 *   同一个 captchaId 只能被校验一次，截获也无法重放。
 *   （不用 Redis 6.2+ 的 GETDEL 命令是因为本机 Redis 是 3.2，Lua 脚本任何版本都支持且语义相同）
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    /** 验证码字符表：去掉了 I/L/O/0/1 等容易混淆的字符，减少"用户看不清输错"的体验问题 */
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    /** 验证码长度：4 位（平衡可读性和安全性） */
    private static final int CODE_LENGTH = 4;
    /** 过期时间：5 分钟，足够用户慢慢输入，又不至于给攻击者太长的窗口 */
    private static final Duration TTL = Duration.ofMinutes(5);
    /** key 前缀：captcha:{id}，加业务前缀便于区分和运维排查 */
    private static final String KEY_PREFIX = "captcha:";

    /**
     * 原子"取出并删除"Lua 脚本（等价于 Redis 6.2 的 GETDEL，兼容 3.2）
     * Redis 执行 Lua 脚本期间不会插入其他命令，所以 GET 和 DEL 之间
     * 不可能被并发请求插队，一次性语义有绝对保证。
     */
    private static final DefaultRedisScript<String> GET_DEL_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]) " +
            "if v then redis.call('DEL', KEYS[1]) end " +
            "return v", String.class);

    /** 密码学安全的随机数生成器，比 java.util.Random 更难被预测 */
    private final SecureRandom random = new SecureRandom();
    private final StringRedisTemplate redis;

    /**
     * 生成一张验证码
     * <p>
     * 流程：随机取 4 位验证码 → 生成 UUID 作为 id → 存入 Redis（带 5 分钟 TTL）→ 画图 → 返回
     *
     * @return CaptchaVO {id: UUID, image: "data:image/png;base64,...."}
     *         image 带了 data:image/png;base64, 前缀，前端可以直接绑定到 img 的 src 属性
     */
    public CaptchaVO generate() {
        // 从字符表里随机取 4 个字符，拼成验证码
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }

        // 用 UUID 作为验证码的唯一标识，前端拿到后登录时回传这个 id
        String id = UUID.randomUUID().toString();

        // 存入 Redis：SET captcha:{id} code EX 300
        // TTL 到期由 Redis 自动删除 —— 替代了旧版 ConcurrentHashMap + 手写惰性清理
        redis.opsForValue().set(KEY_PREFIX + id, sb.toString(), TTL);

        // draw() 用 AWT 画验证码图片 → 返回 PNG 二进制 → Base64 编码 → 拼上 data: 前缀
        return new CaptchaVO(id, "data:image/png;base64,"
                + Base64.getEncoder().encodeToString(draw(sb.toString())));
    }

    /**
     * 校验并销毁验证码（一次性使用）
     * <p>
     * 核心安全机制：Lua 脚本原子地"取出 + 删除"。
     * 这意味着同一个验证码 id 只能被校验一次：
     * - 第一次校验：脚本取出 code → 比对 → 匹配返回 true → key 已被脚本删除
     * - 第二次用同一个 id：Redis 里已经没有了 → 返回 false
     * 这样即使攻击者截获了 captchaId，也无法重放使用。
     *
     * @param id   验证码标识（generate 时返回的 UUID）
     * @param code 用户输入的验证码文本
     * @return true=校验通过，false=验证码不存在/已过期/已使用/不匹配
     */
    public boolean verifyAndConsume(String id, String code) {
        // 空值检查
        if (id == null || code == null) {
            return false;
        }
        // 原子取出并删除：EXISTS 的 key 返回 null（不存在 / 已过期 / 已用过一次）
        String stored = redis.execute(GET_DEL_SCRIPT, List.of(KEY_PREFIX + id));
        if (stored == null) {
            return false;
        }
        // equalsIgnoreCase：忽略大小写（A 和 a 都算对），提升用户体验
        // trim()：去掉首尾空格，防止用户不小心多打了空格
        return stored.equalsIgnoreCase(code.trim());
    }

    /**
     * 用 Java AWT 的 Graphics2D 手绘验证码图片
     * <p>
     * 绘制层次：
     * 1. 背景：浅灰色矩形填充
     * 2. 干扰线：5 条随机位置、随机颜色的直线（增加机器识别难度）
     * 3. 字符：4 个验证码字符，每个字符随机旋转 ±0.25 弧度（约 ±14°），随机深色
     * 4. 干扰点：30 个随机位置的圆点
     * <p>
     * 这些干扰元素的目的：让 OCR（光学字符识别）软件难以正确提取验证码文本，
     * 但人类肉眼仍然能轻松辨认。
     *
     * @param code 验证码文本（4 个字符）
     * @return PNG 格式的图片字节数组
     */
    private byte[] draw(String code) {
        int width = 110;   // 图片宽度
        int height = 40;   // 图片高度

        // 创建 BufferedImage：TYPE_INT_RGB 表示用 RGB 色彩模式，不带透明通道
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 开启抗锯齿，让字符边缘更平滑
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // —— 第 1 层：背景填充 ——
        g.setColor(new Color(245, 247, 250)); // 浅灰色
        g.fillRect(0, 0, width, height);

        // —— 第 2 层：5 条干扰线 ——
        for (int i = 0; i < 5; i++) {
            g.setColor(randomLightColor()); // 浅色，不遮挡字符
            // 随机起点和终点
            g.drawLine(random.nextInt(width), random.nextInt(height),
                    random.nextInt(width), random.nextInt(height));
        }

        // —— 第 3 层：验证码字符 ——
        g.setFont(new Font("Arial", Font.BOLD, 28));
        for (int i = 0; i < code.length(); i++) {
            g.setColor(randomDarkColor()); // 深色，确保和背景有足够对比度
            // 随机旋转角度：(random - 0.5) * 0.5 → 范围约 ±0.25 弧度（±14°）
            double angle = (random.nextDouble() - 0.5) * 0.5;
            // 字符 x 坐标：等间距排列，每个字符占 25px
            int x = 10 + i * 25;
            // 字符 y 坐标：加一点随机偏移，让字符不在同一水平线上
            int y = 28 + random.nextInt(5) - 2;
            // rotate：以 (x, y) 为中心旋转画布
            g.rotate(angle, x, y);
            // drawString：在 (x, y) 处绘制字符
            g.drawString(String.valueOf(code.charAt(i)), x, y);
            // 旋转回来，为下一个字符准备
            g.rotate(-angle, x, y);
        }

        // —— 第 4 层：30 个干扰点 ——
        for (int i = 0; i < 30; i++) {
            g.setColor(randomLightColor());
            // fillOval：画一个 2x2 像素的小圆点
            g.fillOval(random.nextInt(width), random.nextInt(height), 2, 2);
        }

        // 释放 Graphics2D 资源（底层会调用 native 的 dispose）
        g.dispose();

        // 把 BufferedImage 转成 PNG 字节数组
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            // 正常情况下不会失败（内存流不会出 IO 错误），这里只是防御性处理
            throw new IllegalStateException("生成验证码图片失败", e);
        }
    }

    /** 随机深色：RGB 分量都在 0~119 之间，确保字符在浅色背景上可读 */
    private Color randomDarkColor() {
        return new Color(random.nextInt(120), random.nextInt(120), random.nextInt(120));
    }

    /** 随机浅色：RGB 分量都在 160~239 之间，作为干扰线和干扰点的颜色 */
    private Color randomLightColor() {
        return new Color(160 + random.nextInt(80), 160 + random.nextInt(80), 160 + random.nextInt(80));
    }
}
