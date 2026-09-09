package com.macro.mall.portal.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 前台提交商品评价参数。 */
@Getter
@Setter
public class ProductCommentParam {
    @NotNull
    private Long orderItemId;

    @NotNull
    @Min(1)
    @Max(5)
    @Schema(description = "评价星数，1-5")
    private Integer star;

    @NotBlank
    @Size(max = 500)
    private String content;

    @Schema(description = "图片地址，多个地址用逗号分隔")
    @Size(max = 1000)
    private String pics;
}
