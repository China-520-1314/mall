package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.mapper.UmsMemberLevelMapper;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberExample;
import com.macro.mall.model.UmsMemberLevel;
import com.macro.mall.model.UmsMemberLevelExample;
import com.macro.mall.portal.domain.MemberDetails;
import com.macro.mall.portal.dao.UmsMemberEmailDao;
import com.macro.mall.portal.domain.EmailCodePurpose;
import com.macro.mall.portal.domain.EmailCodeSendResult;
import com.macro.mall.portal.service.EmailVerificationService;
import com.macro.mall.portal.service.UmsMemberCacheService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.security.util.JwtTokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;

/**
 * 会员管理Service实现类
 * Created by macro on 2018/8/3.
 */
@Service
public class UmsMemberServiceImpl implements UmsMemberService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UmsMemberServiceImpl.class);
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    @Autowired
    private UmsMemberMapper memberMapper;
    @Autowired
    private UmsMemberLevelMapper memberLevelMapper;
    @Autowired
    private UmsMemberCacheService memberCacheService;
    @Autowired
    private UmsMemberEmailDao memberEmailDao;
    @Autowired
    private EmailVerificationService emailVerificationService;

    @Override
    public UmsMember getByUsername(String username) {
        UmsMember member = memberCacheService.getMember(username);
        if(member!=null) return member;
        UmsMemberExample example = new UmsMemberExample();
        example.createCriteria().andUsernameEqualTo(username);
        List<UmsMember> memberList = memberMapper.selectByExample(example);
        if (!CollectionUtils.isEmpty(memberList)) {
            member = memberList.get(0);
            memberCacheService.setMember(member);
            return member;
        }
        return null;
    }

    @Override
    public UmsMember getById(Long id) {
        return memberMapper.selectByPrimaryKey(id);
    }

    @Override
    public void register(String password, String confirmPassword, String email, String authCode) {
        validatePassword(password);
        if (!password.equals(confirmPassword)) {
            Asserts.fail("两次输入的密码不一致");
        }
        String normalizedEmail = EmailVerificationServiceImpl.normalizeAndValidate(email);
        String username = normalizedEmail;
        //查询是否已有该用户
        UmsMemberExample example = new UmsMemberExample();
        example.createCriteria().andUsernameEqualTo(username);
        List<UmsMember> umsMembers = memberMapper.selectByExample(example);
        if (!CollectionUtils.isEmpty(umsMembers)) {
            Asserts.fail("该QQ邮箱已经注册");
        }
        if (memberEmailDao.countByEmail(normalizedEmail) > 0) {
            Asserts.fail("该邮箱已经注册");
        }
        emailVerificationService.verifyCode(normalizedEmail, authCode, EmailCodePurpose.REGISTER);
        //没有该用户进行添加操作
        UmsMember umsMember = new UmsMember();
        umsMember.setUsername(username);
        umsMember.setPassword(passwordEncoder.encode(password));
        umsMember.setCreateTime(new Date());
        umsMember.setStatus(1);
        //获取默认会员等级并设置
        UmsMemberLevelExample levelExample = new UmsMemberLevelExample();
        levelExample.createCriteria().andDefaultStatusEqualTo(1);
        List<UmsMemberLevel> memberLevelList = memberLevelMapper.selectByExample(levelExample);
        if (!CollectionUtils.isEmpty(memberLevelList)) {
            umsMember.setMemberLevelId(memberLevelList.get(0).getId());
        }
        memberMapper.insert(umsMember);
        memberEmailDao.updateEmail(umsMember.getId(), normalizedEmail);
        umsMember.setPassword(null);
    }

    @Override
    public EmailCodeSendResult sendEmailCode(String email, EmailCodePurpose purpose) {
        String normalizedEmail = EmailVerificationServiceImpl.normalizeAndValidate(email);
        int registeredCount = memberEmailDao.countByEmail(normalizedEmail);
        if (purpose == EmailCodePurpose.REGISTER && registeredCount > 0) {
            Asserts.fail("该邮箱已经注册");
        }
        if (purpose == EmailCodePurpose.RESET_PASSWORD && registeredCount == 0) {
            Asserts.fail("该邮箱尚未注册");
        }
        return emailVerificationService.sendCode(normalizedEmail, purpose);
    }

    @Override
    public void updatePassword(String email, String password, String authCode) {
        validatePassword(password);
        String normalizedEmail = EmailVerificationServiceImpl.normalizeAndValidate(email);
        Long memberId = memberEmailDao.selectMemberIdByEmail(normalizedEmail);
        if (memberId == null) {
            Asserts.fail("该邮箱尚未注册");
        }
        emailVerificationService.verifyCode(normalizedEmail, authCode, EmailCodePurpose.RESET_PASSWORD);
        UmsMember member = memberMapper.selectByPrimaryKey(memberId);
        member.setPassword(passwordEncoder.encode(password));
        memberMapper.updateByPrimaryKeySelective(member);
        memberCacheService.delMember(memberId);
    }

    @Override
    public void changePassword(String oldPassword, String newPassword, String confirmPassword) {
        if (oldPassword == null || oldPassword.isBlank()) {
            Asserts.fail("请输入当前密码");
        }
        validatePassword(newPassword);
        if (!newPassword.equals(confirmPassword)) {
            Asserts.fail("两次输入的新密码不一致");
        }
        UmsMember currentMember = getCurrentMember();
        UmsMember member = memberMapper.selectByPrimaryKey(currentMember.getId());
        if (member == null || !passwordEncoder.matches(oldPassword, member.getPassword())) {
            Asserts.fail("当前密码不正确");
        }
        if (passwordEncoder.matches(newPassword, member.getPassword())) {
            Asserts.fail("新密码不能与当前密码相同");
        }
        member.setPassword(passwordEncoder.encode(newPassword));
        memberMapper.updateByPrimaryKeySelective(member);
        memberCacheService.delMember(member.getId());
    }

    @Override
    public UmsMember getCurrentMember() {
        SecurityContext ctx = SecurityContextHolder.getContext();
        Authentication auth = ctx.getAuthentication();
        MemberDetails memberDetails = (MemberDetails) auth.getPrincipal();
        return memberDetails.getUmsMember();
    }

    @Override
    public void updateIntegration(Long id, Integer integration) {
        UmsMember record=new UmsMember();
        record.setId(id);
        record.setIntegration(integration);
        memberMapper.updateByPrimaryKeySelective(record);
        memberCacheService.delMember(id);
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        UmsMember member = getByUsername(username);
        if(member!=null){
            return new MemberDetails(member);
        }
        throw new UsernameNotFoundException("用户名或密码错误");
    }

    @Override
    public String login(String email, String password) {
        String token = null;
        try {
            String normalizedEmail = EmailVerificationServiceImpl.normalizeAndValidate(email);
            Long memberId = memberEmailDao.selectMemberIdByEmail(normalizedEmail);
            UmsMember member = memberId == null ? null : memberMapper.selectByPrimaryKey(memberId);
            if (member == null) {
                throw new UsernameNotFoundException("QQ邮箱或密码错误");
            }
            UserDetails userDetails = new MemberDetails(member);
            if(!passwordEncoder.matches(password,userDetails.getPassword())){
                throw new BadCredentialsException("密码不正确");
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            token = jwtTokenUtil.generateToken(userDetails);
        } catch (AuthenticationException e) {
            LOGGER.warn("登录异常:{}", e.getMessage());
        }
        return token;
    }

    @Override
    public String refreshToken(String token) {
        return jwtTokenUtil.refreshHeadToken(token);
    }

    private void validatePassword(String password) {
        if (password == null || !password.matches("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,20}$")) {
            Asserts.fail("密码须为8到20位字母和数字组合");
        }
    }

}
