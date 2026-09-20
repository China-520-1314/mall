package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.ProductCommentParam;

import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsComment;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.portal.domain.PmsPortalProductDetail;
import com.macro.mall.portal.domain.ProductCommentBatchParam;
import com.macro.mall.portal.domain.ProductCommentSummary;
import com.macro.mall.portal.domain.PmsProductCategoryNode;

import java.util.List;

/**
 * 前台商品管理Service
 * Created by macro on 2020/4/6.
 */
public interface PmsPortalProductService {
    /**
     * 综合搜索商品
     */
    List<PmsProduct> search(String keyword, Long brandId, Long productCategoryId, Integer pageNum, Integer pageSize, Integer sort);

    /**
     * 以树形结构获取所有商品分类
     */
    List<PmsProductCategoryNode> categoryTreeList();

    /**
     * 获取前台商品详情
     */
    PmsPortalProductDetail detail(Long id);

    CommonPage<PmsComment> listComments(Long productId, Integer pageNum, Integer pageSize);

    ProductCommentSummary getCommentSummary(Long productId);

    void createComments(ProductCommentBatchParam param, String clientIp);
    void createProductComment(ProductCommentParam param, String clientIp);
}
