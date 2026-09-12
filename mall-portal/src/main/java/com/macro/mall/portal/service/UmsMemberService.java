package com.macro.mall.portal.service;

import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.EmailCodePurpose;
import com.macro.mall.portal.domain.EmailCodeSendResult;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会员管理Service
 * Created by macro on 2018/8/3.
 */
public interface UmsMemberService {
    /**
     * 根据用户名获取会员
     */
    UmsMember getByUsername(String username);

    /**
     * 根据会员编号获取会员
     */
    UmsMember getById(Long id);

    /**
     * 用户注册
     */
    @Transactional
    void register(String password, String confirmPassword, String email, String authCode);

    /**
     * 发送注册或重置密码验证码
     */
    EmailCodeSendResult sendEmailCode(String email, EmailCodePurpose purpose);

    /**
     * 通过邮箱验证码重置密码
     */
    @Transactional
    void updatePassword(String email, String password, String authCode);

    /**
     * 修改当前登录会员密码。
     */
    @Transactional
    void changePassword(String oldPassword, String newPassword, String confirmPassword);

    /**
     * 获取当前登录会员
     */
    UmsMember getCurrentMember();

    /**
     * 根据会员id修改会员积分
     */
    void updateIntegration(Long id,Integer integration);


    /**
     * 获取用户信息
     */
    UserDetails loadUserByUsername(String username);

    /**
     * 登录后获取token
     */
    String login(String email, String password);

    /**
     * 刷新token
     */
    String refreshToken(String token);
}
