package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.assistant.AssistantChatRequest;
import com.macro.mall.portal.assistant.AssistantChatResponse;
import com.macro.mall.portal.assistant.AssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "AssistantController", description = "商城智能客服")
@RequestMapping("/assistant")
public class AssistantController {
    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @Operation(summary = "发送客服消息")
    @PostMapping("/chat")
    public CommonResult<AssistantChatResponse> chat(@RequestBody AssistantChatRequest request) {
        try {
            return CommonResult.success(assistantService.chat(request));
        } catch (IllegalArgumentException ex) {
            return CommonResult.validateFailed(ex.getMessage());
        }
    }
}
