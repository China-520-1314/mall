package com.macro.mall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** 管理端向订单用户发送的站内信。 */
@Getter
@Setter
public class UmsMemberMessageParam {
    @NotBlank(message = "消息标题不能为空")
    private String title;

    @NotBlank(message = "消息内容不能为空")
    private String content;
}
