package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.assistant.AssistantBusinessService;
import com.macro.mall.portal.assistant.AssistantBusinessSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 完整商城模式下的客服业务摘要接口，依赖当前登录用户和数据库。 */
@RestController
@Profile("!assistant")
@Tag(name = "AssistantBusinessController", description = "智能客服业务摘要")
@RequestMapping("/assistant")
public class AssistantBusinessController {
    private final AssistantBusinessService businessService;

    public AssistantBusinessController(AssistantBusinessService businessService) {
        this.businessService = businessService;
    }

    @Operation(summary = "查询当前用户的客服业务摘要")
    @PostMapping("/business-summary")
    public CommonResult<AssistantBusinessSummary> businessSummary(@RequestParam String type) {
        try {
            return CommonResult.success(businessService.summary(type));
        } catch (IllegalArgumentException ex) {
            return CommonResult.validateFailed(ex.getMessage());
        }
    }
}
