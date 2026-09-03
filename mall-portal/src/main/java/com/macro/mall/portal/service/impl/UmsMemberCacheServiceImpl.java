package com.macro.mall.portal.service.impl;

import com.macro.mall.common.service.RedisService;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.service.UmsMemberCacheService;
import com.macro.mall.security.annotation.CacheException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UmsMemberCacheService实现类
 * Created by macro on 2020/3/14.
 */
@Service
public class UmsMemberCacheServiceImpl implements UmsMemberCacheService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UmsMemberCacheServiceImpl.class);

    @Autowired
    private RedisService redisService;
    @Autowired
    private UmsMemberMapper memberMapper;
    @Value("${redis.database}")
    private String REDIS_DATABASE;
    @Value("${redis.expire.common}")
    private Long REDIS_EXPIRE;
    @Value("${redis.expire.authCode}")
    private Long REDIS_EXPIRE_AUTH_CODE;
    @Value("${redis.key.member}")
    private String REDIS_KEY_MEMBER;
    @Value("${redis.key.authCode}")
    private String REDIS_KEY_AUTH_CODE;
    /**
     * 开发环境 Redis 不可用时，验证码临时保存在当前进程，避免注册页面直接返回 500。
     * 生产环境默认关闭，仍要求使用 Redis 保证多实例一致性。
     */
    @Value("${redis.fallback.auth-code-enabled:false}")
    private boolean authCodeFallbackEnabled;

    private final Map<String, LocalAuthCode> localAuthCodes = new ConcurrentHashMap<>();
    private final AtomicBoolean fallbackWarningLogged = new AtomicBoolean(false);

    @Override
    public void delMember(Long memberId) {
        UmsMember umsMember = memberMapper.selectByPrimaryKey(memberId);
        if (umsMember != null) {
            String key = REDIS_DATABASE + ":" + REDIS_KEY_MEMBER + ":" + umsMember.getUsername();
            try {
                redisService.del(key);
            } catch (RuntimeException ex) {
                LOGGER.debug("Redis 不可用，跳过会员缓存删除：{}", ex.getMessage());
            }
        }
    }

    @Override
    public UmsMember getMember(String username) {
        String key = REDIS_DATABASE + ":" + REDIS_KEY_MEMBER + ":" + username;
        try {
            return (UmsMember) redisService.get(key);
        } catch (RuntimeException ex) {
            LOGGER.debug("Redis 不可用，跳过会员缓存读取：{}", ex.getMessage());
            return null;
        }
    }

    @Override
    public void setMember(UmsMember member) {
        String key = REDIS_DATABASE + ":" + REDIS_KEY_MEMBER + ":" + member.getUsername();
        try {
            redisService.set(key, member, REDIS_EXPIRE);
        } catch (RuntimeException ex) {
            LOGGER.debug("Redis 不可用，跳过会员缓存写入：{}", ex.getMessage());
        }
    }

    @CacheException
    @Override
    public void setAuthCode(String telephone, String authCode) {
        String key = REDIS_DATABASE + ":" + REDIS_KEY_AUTH_CODE + ":" + telephone;
        try {
            redisService.set(key, authCode, REDIS_EXPIRE_AUTH_CODE);
            localAuthCodes.remove(key);
            fallbackWarningLogged.set(false);
        } catch (RuntimeException ex) {
            if (!authCodeFallbackEnabled) {
                throw ex;
            }
            long expireAt = System.currentTimeMillis()
                    + Math.max(1L, REDIS_EXPIRE_AUTH_CODE == null ? 90L : REDIS_EXPIRE_AUTH_CODE) * 1000L;
            localAuthCodes.put(key, new LocalAuthCode(authCode, expireAt));
            logFallbackWarning(ex);
        }
    }

    @CacheException
    @Override
    public String getAuthCode(String telephone) {
        String key = REDIS_DATABASE + ":" + REDIS_KEY_AUTH_CODE + ":" + telephone;
        try {
            Object value = redisService.get(key);
            if (value instanceof String code) {
                fallbackWarningLogged.set(false);
                return code;
            }
        } catch (RuntimeException ex) {
            if (!authCodeFallbackEnabled) {
                throw ex;
            }
            logFallbackWarning(ex);
        }

        LocalAuthCode localAuthCode = localAuthCodes.get(key);
        if (localAuthCode == null) {
            return null;
        }
        if (localAuthCode.expireAt() <= System.currentTimeMillis()) {
            localAuthCodes.remove(key, localAuthCode);
            return null;
        }
        return localAuthCode.code();
    }

    private void logFallbackWarning(RuntimeException ex) {
        if (fallbackWarningLogged.compareAndSet(false, true)) {
            LOGGER.warn("Redis 不可用，开发环境验证码暂存于当前进程：{}", ex.getMessage());
        }
    }

    private record LocalAuthCode(String code, long expireAt) {
    }
}
