package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.EmailCodePurpose;
import com.macro.mall.portal.domain.EmailCodeSendResult;
import com.macro.mall.portal.domain.EmailCodeRateLimitException;
import com.macro.mall.portal.service.UmsMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;

/**
 * 会员管理Controller
 * Created by macro on 2018/8/3.
 */
@Controller
@Tag(name = "UmsMemberController", description = "会员登录注册管理")
@RequestMapping("/sso")
@Validated
public class UmsMemberController {
    @Value("${jwt.tokenHeader}")
    private String tokenHeader;
    @Value("${jwt.tokenHead}")
    private String tokenHead;
    @Autowired
    private UmsMemberService memberService;

    @Operation(summary = "会员注册")
    @RequestMapping(value = "/register", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<Void> register(@RequestParam String password,
                                       @RequestParam String confirmPassword,
                                       @RequestParam String email,
                                       @RequestParam String authCode) {
        memberService.register(password, confirmPassword, email, authCode);
        return CommonResult.success(null,"注册成功");
    }

    @Operation(summary = "发送QQ邮箱验证码")
    @RequestMapping(value = "/sendEmailCode", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<EmailCodeSendResult> sendEmailCode(@RequestParam String email,
                                             @RequestParam EmailCodePurpose purpose) {
        try {
            return CommonResult.success(memberService.sendEmailCode(email, purpose), "验证码已发送，请查收QQ邮箱");
        } catch (EmailCodeRateLimitException exception) {
            CommonResult<EmailCodeSendResult> result = CommonResult.failed(exception.getMessage());
            result.setCode(429);
            result.setData(new EmailCodeSendResult(email, exception.getRetryAfterSeconds(), 0));
            return result;
        }
    }

    @Operation(summary = "通过邮箱验证码重置密码")
    @RequestMapping(value = "/updatePassword", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<Void> updatePassword(@RequestParam String email,
                                              @RequestParam String password,
                                              @RequestParam String authCode) {
        memberService.updatePassword(email, password, authCode);
        return CommonResult.success(null, "密码重置成功");
    }

    @Operation(summary = "修改当前登录会员密码")
    @RequestMapping(value = "/changePassword", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<Void> changePassword(@RequestParam @NotBlank String oldPassword,
                                              @RequestParam @NotBlank String newPassword,
                                              @RequestParam @NotBlank String confirmPassword,
                                              Principal principal) {
        if (principal == null) {
            return CommonResult.unauthorized(null);
        }
        memberService.changePassword(oldPassword, newPassword, confirmPassword);
        return CommonResult.success(null, "密码修改成功，请重新登录");
    }

    @Operation(summary = "会员登录")
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult login(@RequestParam String email,
                              @RequestParam String password) {
        String token = memberService.login(email, password);
        if (token == null) {
            return CommonResult.validateFailed("QQ邮箱或密码错误");
        }
        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put("token", token);
        tokenMap.put("tokenHead", tokenHead);
        return CommonResult.success(tokenMap);
    }

    @Operation(summary = "获取会员信息")
    @RequestMapping(value = "/info", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult info(Principal principal) {
        if(principal==null){
            return CommonResult.unauthorized(null);
        }
        UmsMember member = memberService.getCurrentMember();
        return CommonResult.success(member);
    }


    @Operation(summary = "刷新token")
    @RequestMapping(value = "/refreshToken", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult refreshToken(HttpServletRequest request) {
        String token = request.getHeader(tokenHeader);
        String refreshToken = memberService.refreshToken(token);
        if (refreshToken == null) {
            return CommonResult.failed("token已经过期！");
        }
        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put("token", refreshToken);
        tokenMap.put("tokenHead", tokenHead);
        return CommonResult.success(tokenMap);
    }
}
