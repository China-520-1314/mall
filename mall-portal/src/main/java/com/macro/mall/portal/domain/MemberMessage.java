package com.macro.mall.portal.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/** 会员站内信。 */
@Getter
@Setter
public class MemberMessage {
    private Long id;
    private Long orderId;
    private String title;
    private String content;
    private Integer readStatus;
    private Date createTime;
}
