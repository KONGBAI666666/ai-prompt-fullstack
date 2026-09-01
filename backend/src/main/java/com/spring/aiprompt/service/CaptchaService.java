package com.spring.aiprompt.service;

import com.spring.aiprompt.vo.CaptchaVO;
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
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图形验证码服务 —— 防止机器人暴力撞库登录的安全组件
 * <p>
 * 整体流程：
 * 1. 前端打开登录页 → 调 GET /user/captcha → 后端 generate() 生成验证码图 → 返回 {id, base64图片}
 * 2. 前端把图片显示在 img 标签里，用户人眼识别验证码
 * 3. 用户提交登录 → 带上 captchaId + captchaCode → 后端 verifyAndConsume() 校验
 * 4. 校验通过 → 验证码从内存中删除（一次性）→ 继续走登录流程
 * 5. 校验失败 → 返回"验证码错误"→ 前端自动刷新一张新图
 * <p>
 * 技术选型：
 * - 生成：用 Java AWT（Graphics2D）手绘图片，不依赖任何第三方图形库
 * - 存储：ConcurrentHashMap（内存），不用数据库——验证码是短生命周期数据，存数据库白白增加 IO
 * - 过期：TTL 5 分钟，每次 generate 时顺便清理过期条目（惰性清理，不需要定时器线程）
 * - 安全：SecureRandom（密码学安全随机数生成器），比普通 Random 更难预测
 * - 一次性：verifyAndConsume 用 store.remove(id)，校验和销毁是同一次原子操作
 * <p>
 * 如果要分布式部署（多台服务器），把 ConcurrentHashMap 换成 Redis 即可：
 *   Redis SET key value EX 300 → 存验证码，5分钟自动过期
 *   Redis GETDEL key → 取出并删除（等价于 remove）
 *   接口签名完全不用变
 */
@Service
public class CaptchaService {

    /** 验证码字符表：去掉了 I/L/O/0/1 等容易混淆的字符，减少"用户看不清输错"的体验问题 */
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    /** 验证码长度：4 位（平衡可读性和安全性） */
    private static final int CODE_LENGTH = 4;
    /** 过期时间：5 分钟（毫秒），足够用户慢慢输入，又不至于给攻击者太长的窗口 */
    private static final long TTL_MILLIS = 5 * 60 * 1000L;

    /** 密码学安全的随机数生成器，比 java.util.Random 更难被预测 */
    private final SecureRandom random = new SecureRandom();
    /** 验证码存储：Map<验证码id, Entry(code, expireAt)>，ConcurrentHashMap 保证多线程并发安全 */
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /**
     * 生成一张验证码
     * <p>
     * 流程：清理过期验证码 → 随机取 4 位验证码 → 生成 UUID 作为 id → 存入 Map → 画图 → 返回
     *
     * @return CaptchaVO {id: UUID, image: "data:image/png;base64,...."}
     *         image 带了 data:image/png;base64, 前缀，前端可以直接绑定到 img 的 src 属性
     */
    public CaptchaVO generate() {
        // 惰性清理：每次生成新验证码时，顺便把过期的老验证码清掉
        // 不用单独起一个定时器线程，减少资源消耗
        evictExpired();

        // 从字符表里随机取 4 个字符，拼成验证码
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }

        // 用 UUID 作为验证码的唯一标识，前端拿到后登录时回传这个 id
        String id = UUID.randomUUID().toString();

        // 存入 Map：code = 验证码文本，expireAt = 过期时间戳
        store.put(id, new Entry(sb.toString(), System.currentTimeMillis() + TTL_MILLIS));

        // draw() 用 AWT 画验证码图片 → 返回 PNG 二进制 → Base64 编码 → 拼上 data: 前缀
        return new CaptchaVO(id, "data:image/png;base64,"
                + Base64.getEncoder().encodeToString(draw(sb.toString())));
    }

    /**
     * 校验并销毁验证码（一次性使用）
     * <p>
     * 核心安全机制：store.remove(id) —— 取出的同时立即从 Map 中删除。
     * 这意味着同一个验证码 id 只能被校验一次：
     * - 第一次校验：从 Map 取出 code → 比对 → 匹配返回 true → 验证码已从 Map 删除
     * - 第二次用同一个 id：Map 里已经没有了 → 返回 false
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
        // 关键操作：remove 同时完成"取出"和"删除"两个动作，是原子操作
        Entry entry = store.remove(id);
        // entry == null：验证码不存在（可能是 id 错了，或者已经被用过一次了）
        // 时间戳检查：验证码已过期
        if (entry == null || System.currentTimeMillis() > entry.expireAt()) {
            return false;
        }
        // equalsIgnoreCase：忽略大小写（A 和 a 都算对），提升用户体验
        // trim()：去掉首尾空格，防止用户不小心多打了空格
        return entry.code().equalsIgnoreCase(code.trim());
    }

    /**
     * 惰性清理：遍历 Map，删除所有已过期的验证码
     * 在每次 generate() 时调用，不需要独立的定时器线程
     */
    private void evictExpired() {
        long now = System.currentTimeMillis();
        // removeIf：遍历 Map 的 entrySet，如果 lambda 返回 true 就删除该条目
        store.entrySet().removeIf(e -> e.getValue().expireAt() < now);
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

    /**
     * 验证码存储条目（Java 16+ record）
     * record 是不可变的数据载体，自动生成构造器、getter、equals、hashCode、toString。
     * - code：验证码文本（如 "AB3K"）
     * - expireAt：过期时间戳（System.currentTimeMillis() + TTL_MILLIS）
     */
    private record Entry(String code, long expireAt) {
    }
}
