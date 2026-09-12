package com.macro.mall.portal.domain;

/** 评价点赞结果。 */
public class ProductCommentLikeResult {
    private boolean liked;
    private int likeCount;

    public ProductCommentLikeResult() { }
    public ProductCommentLikeResult(boolean liked, int likeCount) {
        this.liked = liked;
        this.likeCount = likeCount;
    }
    public boolean isLiked() { return liked; }
    public void setLiked(boolean liked) { this.liked = liked; }
    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
}
