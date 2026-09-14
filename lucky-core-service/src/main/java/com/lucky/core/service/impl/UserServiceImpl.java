package com.lucky.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lucky.enums.RoleType;
import com.lucky.core.dto.user.AppointBarAdminDTO;
import com.lucky.core.dto.user.LoginDTO;
import com.lucky.core.dto.RefreshResult;
import com.lucky.core.dto.user.RegisterDTO;
import com.lucky.core.dto.TokenDTO;
import com.lucky.core.dto.user.UpdateProfileDTO;
import com.lucky.core.entity.BarManager;
import com.lucky.core.entity.Role;
import com.lucky.core.entity.User;
import com.lucky.core.entity.UserInfo;
import com.lucky.core.entity.UserPhone;
import com.lucky.core.entity.UserRole;
import com.lucky.core.mapper.BarManagerMapper;
import com.lucky.core.mapper.RoleMapper;
import com.lucky.core.mapper.UserInfoMapper;
import com.lucky.core.mapper.UserMapper;
import com.lucky.core.mapper.UserPhoneMapper;
import com.lucky.core.mapper.UserRoleMapper;
import com.lucky.core.service.LoginAttemptService;
import com.lucky.core.service.TokenIssuer;
import com.lucky.core.service.TokenRedisService;
import com.lucky.core.service.UserService;
import com.lucky.core.utils.JwtUtil;
import com.lucky.core.vo.LoginVO;
import com.lucky.core.vo.RoleVO;
import com.lucky.exception.BusinessException;
import com.lucky.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /** 并发轮换时，落败方等待抢占者写入新 jti 的最长时间(毫秒) */
    private static final long ROTATION_WAIT_MILLIS = 500;

    /** 等待轮换结果的轮询间隔(毫秒) */
    private static final long ROTATION_POLL_MILLIS = 20;

    private final JwtUtil jwtUtil;
    private final TokenIssuer tokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final TokenRedisService tokenRedisService;
    private final LoginAttemptService loginAttemptService;
    private final UserInfoMapper userInfoMapper;
    private final UserPhoneMapper userPhoneMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final BarManagerMapper barManagerMapper;

    @Override
    // 注册跨 4 张表，加事务保证要么全成功、要么全回滚
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO registerDTO) {
        // 1. 先查手机号是否已存在(拦掉绝大多数重复提交)
        boolean exists = lambdaQuery().eq(User::getPhone, registerDTO.getPhone()).exists();
        if (exists) {
            throw new BusinessException("该手机号已注册");
        }
        // 2. 密码以 Bcrypt 加密存储；昵称默认使用用户id（先取雪花id再落库）
        Long id = IdWorker.getId();
        User user = new User();
        user.setId(id);
        user.setPhone(registerDTO.getPhone());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setNickName(String.valueOf(id));
        // 并发兜底：万一两个请求同时通过了上面的查重，靠 tb_user.phone 唯一索引拦下第二个
        try {
            save(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该手机号已注册");
        }

        // 3. 用户扩展信息：粉丝数/关注数初始为 0
        UserInfo userInfo = new UserInfo();
        userInfo.setId(IdWorker.getId());
        userInfo.setUserId(id);
        userInfo.setFans(0);
        userInfo.setFollowee(0);
        userInfoMapper.insert(userInfo);

        // 4. 手机号记录
        UserPhone userPhone = new UserPhone();
        userPhone.setId(IdWorker.getId());
        userPhone.setUserId(id);
        userPhone.setPhone(registerDTO.getPhone());
        userPhoneMapper.insert(userPhone);

        // 5. 默认绑定普通吧友角色：按 role_code 查，避免角色 id 调整后误绑高权限角色
        Role defaultRole = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, RoleType.NORMAL_USER.getCode()));
        if (defaultRole == null) {
            throw new BusinessException("默认角色未配置");
        }
        UserRole userRole = new UserRole();
        userRole.setId(IdWorker.getId());
        userRole.setUserId(id);
        userRole.setRoleId(defaultRole.getId());
        userRoleMapper.insert(userRole);
    }

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        // 0. 防刷：失败次数超限直接拒绝(在查库/验密之前拦截，顺带省掉 BCrypt 开销)
        if (loginAttemptService.isBlocked(loginDTO.getPhone())) {
            throw new BusinessException("登录失败次数过多，请稍后再试");
        }
        // 1. 按手机号查用户
        User user = lambdaQuery().eq(User::getPhone, loginDTO.getPhone()).one();
        // 账号不存在与密码错误统一提示，避免暴露用户是否存在
        // 密码以 Bcrypt 存储，用 matches 比对
        if (user == null || user.getPassword() == null
                || !passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            loginAttemptService.recordFailure(loginDTO.getPhone());
            throw new BusinessException("账号或密码错误");
        }
        // 登录成功：清空失败计数
        loginAttemptService.clear(loginDTO.getPhone());
        // 2. 签发双 Token 并登记会话状态(短 token 返回前端，长 token 由 Controller 写 Cookie)
        TokenDTO tokens = tokenIssuer.issuePair(user.getId()).tokens();
        // 3. 查用户全部角色，角色身份随登录一并返回(前端据此展示/路由)
        List<Role> roles = resolveRoles(user.getId());
        // 4. 组装登录返回体
        LoginVO loginvo = new LoginVO();
        loginvo.setUserId(user.getId());
        loginvo.setPhone(user.getPhone());
        loginvo.setNickName(user.getNickName());
        loginvo.setIcon(user.getIcon());
        loginvo.setAccessToken(tokens.getAccessToken());
        loginvo.setRefreshToken(tokens.getRefreshToken());
        loginvo.setRoles(roles.stream()
                .map(r -> new RoleVO(r.getId(), r.getRoleCode(), r.getRoleName()))
                .toList());
        return loginvo;
    }

    /**
     * 查用户全部角色：tb_user_role 关联 tb_role，一次取回该用户绑定的所有身份
     * <p>一个用户可同时拥有多个角色(如 普通用户 + 商家)，故返回列表而非单条
     *
     * @return 角色列表；未绑定任何角色时返回空列表
     */
    private List<Role> resolveRoles(Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).toList();
        return roleMapper.selectBatchIds(roleIds);
    }

    @Override
    public RefreshResult refreshAccessToken(String refreshToken) {
        // 1. 校验 refresh token：签名/类型/时效任一不过即视为未登录
        JwtUtil.Payload payload = jwtUtil.parse(refreshToken, JwtUtil.TYPE_REFRESH);
        if (payload == null) {
            throw new UnauthorizedException("登录已过期，请重新登录");
        }
        // 2. 当前会话 → 抢占轮换权。并发请求都会先通过上面/这里的校验，
        //    没有抢占就会各自轮换出两个会话
        if (tokenRedisService.isRefreshSessionValid(payload.userId(), payload.jti())) {
            long grace = jwtUtil.getRefreshGraceSeconds();
            if (tokenRedisService.tryClaimRotation(payload.jti(), grace)) {
                // 2a. 抢到了：先登记新会话、再删旧会话(顺序不可反，否则中间存在「零有效会话」窗口)
                JwtUtil.IssuedTokens issued = tokenIssuer.issuePair(payload.userId());
                tokenRedisService.removeRefreshSession(payload.userId(), payload.jti());
                // 把结果写给并发方，让它们能复用同一个新会话
                tokenRedisService.completeRotation(payload.jti(), issued.refreshJti(), grace);
                return new RefreshResult(issued.tokens().getAccessToken(), issued.tokens().getRefreshToken());
            }
            // 2b. 没抢到：已有请求在轮换，等它写好结果后复用同一会话
            RefreshResult reused = reuseRotatedSession(payload.userId(), payload.jti());
            if (reused != null) {
                return reused;
            }
            throw new BusinessException("刷新繁忙，请稍后重试");
        }

        // 3. 非当前会话：可能是刚被轮换(宽限窗口内)，也可能是重放
        RefreshResult graceReuse = reuseRotatedSession(payload.userId(), payload.jti());
        if (graceReuse != null) {
            // 良性重复：新会话还活着，说明不是盗用，只补一个 access
            // (只发 access 不发 refresh：即便真是重放，攻击者也拿不到可链式续期的凭证)
            log.warn("refresh 在宽限窗口内被重复使用，userId={}, jti={}", payload.userId(), payload.jti());
            return graceReuse;
        }

        // 4. 到此无法区分「盗用 / 客户端崩溃 / 响应丢了 / Redis 抖动」。
        //    不做破坏性 kickOut：那会把用户所有设备一起登出，丢响应(移动网络常态)时就是自伤式 DoS。
        //    只拒绝这一次请求，并留下日志供排查/风控。
        log.warn("refresh 复用疑似盗用，userId={}, jti={}", payload.userId(), payload.jti());
        throw new UnauthorizedException("登录状态异常，请重新登录");
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        // 定位用户时必须同时校验「会话仍存活」，不能只凭 token 里的 userId 就 kickOut：
        // 否则任何持有合法密钥的人都能伪造一个 token 强制登出任意用户(定向 DoS)。
        // 其余路径(AuthInterceptor / refreshAccessToken)都查 Redis，这里必须保持一致。
        //
        // access 为主、refresh 兜底，是为了让「access 恰好过期」的正常登出依然能断掉凭证。
        JwtUtil.Payload access = jwtUtil.parse(accessToken, JwtUtil.TYPE_ACCESS);
        if (access != null && tokenRedisService.isAccessWhitelisted(access.userId(), access.jti())) {
            tokenRedisService.kickOut(access.userId());
            return;
        }

        JwtUtil.Payload refresh = jwtUtil.parse(refreshToken, JwtUtil.TYPE_REFRESH);
        if (refresh != null && tokenRedisService.isRefreshSessionValid(refresh.userId(), refresh.jti())) {
            tokenRedisService.kickOut(refresh.userId());
        }
        // 两者都不对应存活会话 → 无可清理(可能已登出/已过期)，调用方清 Cookie 即可
    }

    @Override
    public void kickOut(Long userId) {
        // 踢下线(封号/改密等场景)：access 白名单 + refresh 会话一并清空，两者都即时生效
        tokenRedisService.kickOut(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appointBarAdmin(Long grantUserId, AppointBarAdminDTO dto) {
        // 1. 校验任命人必须是平台管理员
        if (!hasRole(grantUserId)) {
            throw new BusinessException("仅平台管理员可任命吧务");
        }
        // 2. 校验被任命用户存在
        if (getById(dto.getUserId()) == null) {
            throw new BusinessException("被任命用户不存在");
        }
        // 3. 写入任命记录(uk_bar_user 唯一索引兜底：同一用户在同一个吧最多一条吧务身份)
        BarManager barManager = new BarManager();
        barManager.setBarId(dto.getBarId());
        barManager.setUserId(dto.getUserId());
        barManager.setGrantUserId(grantUserId);
        barManager.setStatus(BarManager.STATUS_ACTIVE);
        try {
            barManagerMapper.insert(barManager);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该用户已是本吧吧务");
        }
        // 4. 给目标用户绑定 BAR_ADMIN 角色(幂等：已有则跳过)
        bindRole(dto.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, UpdateProfileDTO dto) {
        // 1. 更新 tb_user：昵称/头像（非空才更新）
        if (dto.getNickName() != null || dto.getIcon() != null) {
            LambdaUpdateWrapper<User> uw = new LambdaUpdateWrapper<>();
            uw.eq(User::getId, userId);
            if (dto.getNickName() != null) {
                uw.set(User::getNickName, dto.getNickName());
            }
            if (dto.getIcon() != null) {
                uw.set(User::getIcon, dto.getIcon());
            }
            update(uw);
        }
        // 2. 更新 tb_user_info：城市/介绍/性别/生日（非空才更新）
        if (dto.getCity() != null || dto.getIntroduce() != null
                || dto.getGender() != null || dto.getBirthday() != null) {
            LambdaUpdateWrapper<UserInfo> uw = new LambdaUpdateWrapper<>();
            uw.eq(UserInfo::getUserId, userId);
            if (dto.getCity() != null) {
                uw.set(UserInfo::getCity, dto.getCity());
            }
            if (dto.getIntroduce() != null) {
                uw.set(UserInfo::getIntroduce, dto.getIntroduce());
            }
            if (dto.getGender() != null) {
                uw.set(UserInfo::getGender, dto.getGender());
            }
            if (dto.getBirthday() != null) {
                uw.set(UserInfo::getBirthday, dto.getBirthday());
            }
            userInfoMapper.update(null, uw);
        }
    }

    /**
     * 判断用户是否拥有指定角色(按角色编码)
     */
    private boolean hasRole(Long userId) {
        return resolveRoles(userId).stream()
                .anyMatch(r -> RoleType.PLATFORM_ADMIN.getCode().equals(r.getRoleCode()));
    }

    /**
     * 给用户绑定角色(幂等：已绑定则跳过)
     */
    private void bindRole(Long userId) {
        Role role = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, RoleType.BAR_ADMIN.getCode()));
        if (role == null) {
            throw new BusinessException("角色未配置：" + RoleType.BAR_ADMIN.getCode());
        }
        boolean exists = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, role.getId())) > 0;
        if (exists) {
            return;
        }
        UserRole userRole = new UserRole();
        userRole.setId(IdWorker.getId());
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        userRoleMapper.insert(userRole);
    }

    /**
     * 复用抢占者刚轮换出的会话：等到新 jti 并确认该会话仍存活，存活则只补发一个 access
     * <p>步骤 2b(并发落败方)与步骤 3(宽限窗口内重复使用)判定条件完全相同，只是后续处置不同
     * ——前者超时算「刷新繁忙」，后者算「疑似盗用」，故抽出本方法、由调用方各自决定。
     *
     * @return 可复用的刷新结果(仅含 access)；无可用会话时返回 null，由调用方决定怎么拒绝
     */
    private RefreshResult reuseRotatedSession(Long userId, String oldJti) {
        String newJti = awaitRotation(oldJti);
        if (newJti == null || !tokenRedisService.isRefreshSessionValid(userId, newJti)) {
            return null;
        }
        return new RefreshResult(tokenIssuer.issueAccessOnly(userId), null);
    }

    /**
     * 读取某个旧 jti 的轮换结果；若抢占者还在轮换中(哨兵值)则短暂轮询等待
     * <p>记录不存在时立即返回 null 不等待——那是「确实没有轮换过」，不是等待中的状态
     *
     * @return 新会话的 jti；无记录或等待超时返回 null
     */
    private String awaitRotation(String oldJti) {
        long deadline = System.currentTimeMillis() + ROTATION_WAIT_MILLIS;
        while (true) {
            String value = tokenRedisService.getRotation(oldJti);
            if (!TokenRedisService.ROTATION_PENDING.equals(value)) {
                return value;
            }
            if (System.currentTimeMillis() >= deadline) {
                return null;
            }
            try {
                Thread.sleep(ROTATION_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }
}
