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
 * 图形验证码：服务端生成 + 一次性校验，防止机器人暴力撞库登录。
 * 内存存储（ConcurrentHashMap），5 分钟过期；登录校验后立即销毁，同一验证码只能用一次。
 */
@Service
public class CaptchaService {

    /** 去掉 I/L/O/0/1 等易混淆字符 */
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 4;
    private static final long TTL_MILLIS = 5 * 60 * 1000L;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /** 生成验证码，返回 id + Base64 图片 */
    public CaptchaVO generate() {
        evictExpired();
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        String id = UUID.randomUUID().toString();
        store.put(id, new Entry(sb.toString(), System.currentTimeMillis() + TTL_MILLIS));
        return new CaptchaVO(id, "data:image/png;base64,"
                + Base64.getEncoder().encodeToString(draw(sb.toString())));
    }

    /** 校验并销毁（一次性）：不存在、过期、不匹配均返回 false */
    public boolean verifyAndConsume(String id, String code) {
        if (id == null || code == null) {
            return false;
        }
        Entry entry = store.remove(id);
        if (entry == null || System.currentTimeMillis() > entry.expireAt()) {
            return false;
        }
        return entry.code().equalsIgnoreCase(code.trim());
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> e.getValue().expireAt() < now);
    }

    /** 用 AWT 画一张带干扰线、干扰点、随机旋转字符的验证码图片 */
    private byte[] draw(String code) {
        int width = 110;
        int height = 40;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(245, 247, 250));
        g.fillRect(0, 0, width, height);

        for (int i = 0; i < 5; i++) {
            g.setColor(randomLightColor());
            g.drawLine(random.nextInt(width), random.nextInt(height),
                    random.nextInt(width), random.nextInt(height));
        }

        g.setFont(new Font("Arial", Font.BOLD, 28));
        for (int i = 0; i < code.length(); i++) {
            g.setColor(randomDarkColor());
            double angle = (random.nextDouble() - 0.5) * 0.5;
            int x = 10 + i * 25;
            int y = 28 + random.nextInt(5) - 2;
            g.rotate(angle, x, y);
            g.drawString(String.valueOf(code.charAt(i)), x, y);
            g.rotate(-angle, x, y);
        }

        for (int i = 0; i < 30; i++) {
            g.setColor(randomLightColor());
            g.fillOval(random.nextInt(width), random.nextInt(height), 2, 2);
        }
        g.dispose();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成验证码图片失败", e);
        }
    }

    private Color randomDarkColor() {
        return new Color(random.nextInt(120), random.nextInt(120), random.nextInt(120));
    }

    private Color randomLightColor() {
        return new Color(160 + random.nextInt(80), 160 + random.nextInt(80), 160 + random.nextInt(80));
    }

    private record Entry(String code, long expireAt) {
    }
}
