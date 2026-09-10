package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.MemberMessage;
import com.macro.mall.portal.service.MemberMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@Tag(name = "MemberMessageController", description = "会员站内信")
@RequestMapping("/member/message")
public class MemberMessageController {
    @Autowired
    private MemberMessageService memberMessageService;

    @Operation(summary = "分页获取站内信")
    @GetMapping("/list")
    @ResponseBody
    public CommonResult<CommonPage<MemberMessage>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return CommonResult.success(memberMessageService.list(pageNum, pageSize));
    }

    @Operation(summary = "获取未读消息数")
    @GetMapping("/unreadCount")
    @ResponseBody
    public CommonResult<Integer> unreadCount() {
        return CommonResult.success(memberMessageService.unreadCount());
    }

    @Operation(summary = "标记站内信已读")
    @PostMapping("/read/{id}")
    @ResponseBody
    public CommonResult<Integer> markRead(@PathVariable Long id) {
        return CommonResult.success(memberMessageService.markRead(id));
    }
}
