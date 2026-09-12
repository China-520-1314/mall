package com.macro.mall.portal.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 商品评价回复参数。 */
public class ProductCommentReplyParam {
    @NotBlank(message = "回复内容不能为空")
    @Size(max = 1000, message = "回复内容不能超过1000个字符")
    private String content;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
