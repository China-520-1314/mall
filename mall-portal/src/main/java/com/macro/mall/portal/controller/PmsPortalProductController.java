package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsComment;
import com.macro.mall.portal.domain.CommentImageResult;
import com.macro.mall.portal.domain.ProductCommentBatchParam;
import com.macro.mall.portal.domain.ProductCommentSummary;
import com.macro.mall.portal.domain.PmsPortalProductDetail;
import com.macro.mall.portal.domain.PmsProductCategoryNode;
import com.macro.mall.portal.service.PmsPortalProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;

/**
 * 前台商品管理Controller
 * Created by macro on 2020/4/6.
 */
@Controller
@Tag(name = "PmsPortalProductController", description = "前台商品管理")
@RequestMapping("/product")
@Validated
public class PmsPortalProductController {

    @Autowired
    private PmsPortalProductService portalProductService;
    @Autowired
    private com.macro.mall.portal.service.CommentImageService commentImageService;

    @Operation(summary = "综合搜索、筛选、排序")
    @Parameter(name = "sort", description = "排序字段:0->按相关度；1->按新品；2->按销量；3->价格从低到高；4->价格从高到低",
            in= ParameterIn.QUERY,schema = @Schema(type = "integer",defaultValue = "0",allowableValues = {"0","1","2","3","4"}))
    @RequestMapping(value = "/search", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<CommonPage<PmsProduct>> search(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Long brandId,
                                                       @RequestParam(required = false) Long productCategoryId,
                                                       @RequestParam(required = false, defaultValue = "0") Integer pageNum,
                                                       @RequestParam(required = false, defaultValue = "5") Integer pageSize,
                                                       @RequestParam(required = false, defaultValue = "0") Integer sort) {
        List<PmsProduct> productList = portalProductService.search(keyword, brandId, productCategoryId, pageNum, pageSize, sort);
        return CommonResult.success(CommonPage.restPage(productList));
    }

    @Operation(summary = "以树形结构获取所有商品分类")
    @RequestMapping(value = "/categoryTreeList", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<PmsProductCategoryNode>> categoryTreeList() {
        List<PmsProductCategoryNode> list = portalProductService.categoryTreeList();
        return CommonResult.success(list);
    }

    @Operation(summary = "获取前台商品详情")
    @RequestMapping(value = "/detail/{id}", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<PmsPortalProductDetail> detail(@PathVariable Long id) {
        PmsPortalProductDetail productDetail = portalProductService.detail(id);
        return CommonResult.success(productDetail);
    }

    @Operation(summary = "分页获取商品评价")
    @RequestMapping(value = "/{productId}/comments", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<CommonPage<PmsComment>> comments(@PathVariable Long productId,
                                                          @RequestParam(required = false, defaultValue = "1") @Min(1) Integer pageNum,
                                                          @RequestParam(required = false, defaultValue = "10") @Min(1) @Max(50) Integer pageSize) {
        return CommonResult.success(portalProductService.listComments(productId, pageNum, pageSize));
    }

    @Operation(summary = "获取商品评价统计")
    @RequestMapping(value = "/{productId}/commentSummary", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<ProductCommentSummary> commentSummary(@PathVariable Long productId) {
        return CommonResult.success(portalProductService.getCommentSummary(productId));
    }

    @Operation(summary = "批量提交订单商品评价")
    @RequestMapping(value = "/comments", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<Void> createComments(@RequestBody @jakarta.validation.Valid ProductCommentBatchParam param,
                                             HttpServletRequest request,
                                             Principal principal) {
        if (principal == null) {
            return CommonResult.unauthorized(null);
        }
        portalProductService.createComments(param, request.getRemoteAddr());
        return CommonResult.success(null, "评价提交成功");
    }

    @Operation(summary = "上传评价图片")
    @RequestMapping(value = "/comment/image", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<CommentImageResult> uploadCommentImage(@RequestPart("file") MultipartFile file,
                                                                Principal principal) {
        if (principal == null) {
            return CommonResult.unauthorized(null);
        }
        String filename = commentImageService.store(file);
        return CommonResult.success(new CommentImageResult("/uploads/comments/" + filename));
    }
}
