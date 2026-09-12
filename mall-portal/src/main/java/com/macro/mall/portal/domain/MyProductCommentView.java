package com.macro.mall.portal.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/** 会员评价记录，不暴露下单和网络信息。 */
@Getter
@Setter
public class MyProductCommentView {
    private Long id;
    private Long productId;
    private String productName;
    private String productPic;
    private Integer star;
    private String content;
    private String pics;
    private Date createTime;
    private Integer showStatus;
    private Integer replayCount;
    private Integer collectCouont;
}
