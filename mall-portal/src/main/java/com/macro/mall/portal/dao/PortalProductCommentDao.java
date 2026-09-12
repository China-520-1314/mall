package com.macro.mall.portal.dao;

import com.macro.mall.model.PmsComment;
import com.macro.mall.model.PmsCommentReplay;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.portal.domain.ProductCommentSummary;
import com.macro.mall.portal.domain.ProductCommentView;
import com.macro.mall.portal.domain.ProductCommentPurchase;
import com.macro.mall.portal.domain.MyProductCommentView;
import com.macro.mall.portal.domain.ReceivedCommentReplyView;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 前台商品评价自定义数据访问接口。 */
public interface PortalProductCommentDao {
    OmsOrder lockOrderForComment(@Param("orderId") Long orderId);

    List<Long> selectCommentedOrderItemIds(@Param("orderItemIds") List<Long> orderItemIds);

    int countByOrderId(@Param("orderId") Long orderId);

    int insert(@Param("comment") PmsComment comment,
               @Param("memberId") Long memberId,
               @Param("orderId") Long orderId,
               @Param("orderItemId") Long orderItemId);

    ProductCommentSummary getSummary(@Param("productId") Long productId);

    PmsComment selectVisibleComment(@Param("commentId") Long commentId);

    PmsComment lockVisibleComment(@Param("commentId") Long commentId);

    List<ProductCommentView> listComments(@Param("productId") Long productId, @Param("memberId") Long memberId);

    List<MyProductCommentView> listMyComments(@Param("memberId") Long memberId);

    List<ReceivedCommentReplyView> listReceivedReplies(@Param("memberId") Long memberId);

    PmsCommentReplay selectReply(@Param("replyId") Long replyId);

    ProductCommentPurchase findCommentPurchase(@Param("productId") Long productId, @Param("memberId") Long memberId);

    int hideComment(@Param("commentId") Long commentId, @Param("memberId") Long memberId);

    List<PmsCommentReplay> listReplies(@Param("commentId") Long commentId);

    int insertReply(@Param("reply") PmsCommentReplay reply);

    int deleteReply(@Param("replyId") Long replyId, @Param("memberId") Long memberId);

    int countReplies(@Param("commentId") Long commentId);

    int addLike(@Param("commentId") Long commentId, @Param("memberId") Long memberId);

    int removeLike(@Param("commentId") Long commentId, @Param("memberId") Long memberId);

    boolean hasLiked(@Param("commentId") Long commentId, @Param("memberId") Long memberId);

    int countLikes(@Param("commentId") Long commentId);
}
