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
 * 用户业务实现 —— 整个系统认证鉴权的核心
 * <p>
 * 职责：用户注册（查重+加密入库）、用户登录（验证码→查用户→比对密码→查状态→发token）、
 *       获取当前用户信息、管理员分页查用户、启用/禁用用户（禁用即踢下线）。
 * <p>
 * 继承 ServiceImpl&lt;UserMapper, User&gt;：这是 MyBatis-Plus 提供的通用 Service 基类，
 * 自动拥有 save、getById、updateById、lambdaQuery、lambdaUpdate、page 等几十个方法，
 * 子类不用写任何 SQL 就能完成基本的增删改查。
 * <p>
 * @RequiredArgsConstructor：Lombok 注解，自动为所有 final 字段生成构造器，
 * 等价于手写构造器注入（Spring 推荐的注入方式，比 @Autowired 字段注入更利于测试和保证不可变性）。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    // —— 依赖注入（通过构造器注入，Spring 自动装配）——
    /** BCrypt 密码加密器：注册时加密明文密码，登录时比对密文 */
    private final BCryptPasswordEncoder passwordEncoder;
    /** 图形验证码服务：登录时校验验证码 */
    private final CaptchaService captchaService;

    /**
     * 用户注册
     * 流程：用户名查重 → 密码 BCrypt 加密 → 组装 User 对象 → save 入库
     * 防护：数据库唯一索引 uk_username 作为并发场景的兜底（两个人同时注册同名用户，
     *       代码层查重可能都返回"不存在"，但数据库唯一约束只会让一条插入成功，另一条抛 DuplicateKeyException）
     *
     * @param dto 注册入参（用户名、密码明文、邮箱）
     */
    @Override
    public void register(RegisterDTO dto) {
        // 第一步：用户名查重 —— 先查库看是否已有同名用户
        // lambdaQuery() 是 MyBatis-Plus 提供的链式查询，等价于 SELECT COUNT(*) FROM sys_user WHERE username = ?
        boolean exists = lambdaQuery().eq(User::getUsername, dto.getUsername()).exists();
        if (exists) {
            // 抛业务异常，会被 GlobalExceptionHandler 捕获并转为 {code:400, message:"用户名已存在"}
            throw new BusinessException("用户名已存在");
        }

        // 第二步：组装 User 实体
        User user = new User();
        user.setUsername(dto.getUsername());
        // BCrypt 加密：每次加密会生成不同的随机盐（salt），即使两个用户密码相同，
        // 密文也不同。这样即使数据库泄露，攻击者也无法通过比对密文反推密码。
        // BCrypt 的密文格式：$2a$10$....（$2a$ 是算法版本，10 是成本因子，后面是盐+哈希）
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setEmail(dto.getEmail());
        // 新注册用户默认角色为 USER（普通用户），不能自行注册为 ADMIN
        user.setRole("USER");
        // 状态 1 = 正常，0 = 禁用
        user.setStatus(1);

        try {
            // save() 由 ServiceImpl 基类提供，等价于 userMapper.insert(user)
            save(user);
        } catch (DuplicateKeyException e) {
            // 兜底防护：极端并发场景下，两个请求同时通过上面的查重，
            // 但数据库唯一索引 uk_username 只允许一条插入成功，另一条会抛 DuplicateKeyException。
            // 这里捕获它并转为用户能看懂的提示，而不是返回 500 堆栈。
            throw new BusinessException("用户名已存在");
        }
    }

    /**
     * 用户登录 —— 系统安全的四道关
     * <p>
     * 关 1：图形验证码（一次性，校验即销毁，防止自动化撞库脚本）
     * 关 2：查用户（根据用户名从数据库取用户记录）
     * 关 3：验密码（BCrypt 比对，且"用户不存在"和"密码错误"返回同一句提示，防止枚举攻击）
     * 关 4：查状态（被禁用的账号不能登录）
     * 四关都过 → StpUtil.login() 生成 token → 返回 {token, user}
     *
     * @param dto 登录入参（用户名、密码、验证码Id、验证码）
     * @return LoginVO 包含 token 和脱敏后的用户信息
     */
    @Override
    public LoginVO login(LoginDTO dto) {
        // —— 第一关：图形验证码 ——
        // verifyAndConsume：校验验证码，校验通过的同时立即从内存中删除（一次性使用）
        // 如果验证码不存在、已过期、或码值不匹配，返回 false
        if (!captchaService.verifyAndConsume(dto.getCaptchaId(), dto.getCaptchaCode())) {
            throw new BusinessException("验证码错误或已过期");
        }

        // —— 第二关：查用户 ——
        // lambdaQuery().eq(User::getUsername, dto.getUsername()).one()
        // 等价于 SELECT * FROM sys_user WHERE username = ? LIMIT 1
        User user = lambdaQuery().eq(User::getUsername, dto.getUsername()).one();

        // —— 第三关：验密码 ——
        // 关键安全设计：用户不存在和密码错误返回同一句"用户名或密码错误"
        // 如果分开提示（"用户不存在" vs "密码错误"），攻击者就能通过提示差异枚举出哪些用户名已注册
        // passwordEncoder.matches(明文, 密文)：BCrypt 比对，内部会从密文中提取盐进行验证
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // —— 第四关：查状态 ——
        // status 为 null（数据异常）或 status != 1（被管理员禁用），都拒绝登录
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用");
        }

        // —— 四关全过，签发 token ——
        // StpUtil.login(id) 是 Sa-Token 的核心方法：
        // 1. 在服务端内存（或 Redis）中创建一个 Session，绑定 userId
        // 2. 生成一个 token（默认 UUID 格式）
        // 3. 建立 token → userId 的映射关系
        // 之后请求只要带上这个 token，Sa-Token 就能识别出"你是谁"
        StpUtil.getLoginIdAsLong();
        StpUtil.login(user.getId());

        // 组装返回数据
        LoginVO vo = new LoginVO();
        // StpUtil.getTokenValue() 获取刚生成的 token 字符串
        vo.setToken(StpUtil.getTokenValue());
        // toVO(user) 将 User 实体转为 UserVO（不含 password），实现密码脱敏
        vo.setUser(toVO(user));
        return vo;
    }

    /**
     * 获取当前登录用户的信息
     * <p>
     * StpUtil.getLoginIdAsLong()：从当前请求的 token 中解析出 userId
     * 能走到这里说明 Sa-Token 过滤器已经校验过登录状态（否则会被 401 拦截）
     *
     * @return 当前用户的脱敏信息（不含密码）
     */
    @Override
    public UserVO getCurrentUser() {
        // StpUtil.getLoginIdAsLong()：从 token 中取出 userId，强转为 Long
        User user = getById(StpUtil.getLoginIdAsLong());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return toVO(user);
    }

    /**
     * 管理员：用户分页列表
     * <p>
     * 先用 lambdaQuery 分页查询 User 实体，再把每条 User 转成 UserVO（脱敏）。
     * 排序：按创建时间倒序（最新的用户排前面）。
     *
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 分页结果，records 里是 UserVO（不含 password）
     */
    @Override
    public Page<UserVO> pageUsers(long pageNum, long pageSize) {
        // lambdaQuery() 链式查询：
        // .orderByDesc(User::getCreateTime) → ORDER BY create_time DESC
        // .page(new Page<>(pageNum, pageSize)) → 自动拼接 LIMIT 和 COUNT
        // 分页插件 PaginationInnerInterceptor（见 MybatisPlusConfig）会自动拦截 SQL 注入 LIMIT
        Page<User> page = lambdaQuery()
                .orderByDesc(User::getCreateTime)
                .page(new Page<>(pageNum, pageSize));

        // 把 User 分页对象转换为 UserVO 分页对象（保留分页信息）
        Page<UserVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        // 逐条转换（用 stream + 方法引用 this::toVO）
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    /**
     * 管理员：启用/禁用用户
     * <p>
     * status 1=正常，0=禁用。
     * 业务规则：不能禁用管理员账号（防止管理员互相禁用导致系统无管理员可用）。
     * 禁用后立即调用 StpUtil.kickout(id) 踢下线 —— 否则被禁用用户已持有的 token
     * 在过期前仍可正常访问（Sa-Token 的 token 有 30 天有效期）。
     * <p>
     * 补充：status 变更会触发数据库触发器 trg_user_status_change，自动往 user_status_log 写一条审计日志。
     *
     * @param id     要操作的用户 id
     * @param status 目标状态（0 或 1）
     */
    @Override
    public void updateUserStatus(Long id, Integer status) {
        // 参数校验：status 只能是 0 或 1
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("status只能为0或1");
        }

        User user = getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 安全规则：不能禁用管理员（避免管理员互相禁用导致锁死）
        if ("ADMIN".equals(user.getRole())) {
            throw new BusinessException("不能禁用管理员账号");
        }

        // 更新状态：updateById 等价于 UPDATE sys_user SET status = ? WHERE id = ?
        // 注意：此时数据库触发器 trg_user_status_change 会在 AFTER UPDATE 时检查
        // NEW.status <> OLD.status，如果状态变了就自动往 user_status_log 插入一条审计记录
        user.setStatus(status);
        updateById(user);

        // 如果是禁用操作（status 从 1→0），立即踢下线
        // StpUtil.kickout(id)：强制让该用户的所有 token 失效，已登录的会话立即断开
        // 这样被禁用的用户无法继续操作系统，即使他浏览器里还存着 token
        if (status == 0) {
            StpUtil.kickout(id);
        }
    }

    /**
     * User → UserVO 转换（密码脱敏）
     * <p>
     * BeanUtils.copyProperties(user, vo)：按字段名复制 user 的属性到 vo。
     * UserVO 里没有 password 字段，所以 password 不会被复制过去 ——
     * 这就是"天然脱敏"：不是"把密码删掉"，而是"根本没有 password 字段可填"。
     * <p>
     * 这种设计的意义：即使用户列表接口被越权访问，密码（哪怕是密文）也不会泄露到网络。
     *
     * @param user 数据库 User 实体
     * @return UserVO（不含 password）
     */
    @Override
    public UserVO toVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
