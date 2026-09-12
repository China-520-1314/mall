package com.macro.mall.portal.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/** 他人对当前会员评价的回复及关联商品。 */
@Getter
@Setter
public class ReceivedCommentReplyView {
    private Long replyId;
    private Long commentId;
    private Long productId;
    private String productName;
    private String productPic;
    private String commentContent;
    private String replyMemberNickName;
    private String replyMemberIcon;
    private String replyContent;
    private Date replyCreateTime;
}
