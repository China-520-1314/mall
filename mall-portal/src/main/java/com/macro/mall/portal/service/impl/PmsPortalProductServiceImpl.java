package com.macro.mall.portal.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.dao.PortalProductCommentDao;
import com.macro.mall.portal.domain.PmsPortalProductDetail;
import com.macro.mall.portal.domain.PmsProductCategoryNode;
import com.macro.mall.portal.domain.ProductCommentBatchParam;
import com.macro.mall.portal.domain.ProductCommentParam;
import com.macro.mall.portal.domain.ProductCommentSummary;
import com.macro.mall.portal.domain.ProductCommentLikeResult;
import com.macro.mall.portal.domain.ProductCommentReplyParam;
import com.macro.mall.portal.domain.ProductCommentView;
import com.macro.mall.portal.domain.ProductCommentPurchase;
import com.macro.mall.portal.domain.MyProductCommentView;
import com.macro.mall.portal.domain.ReceivedCommentReplyView;
import com.macro.mall.portal.domain.MemberDetails;
import com.macro.mall.portal.service.PmsPortalProductService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.exception.Asserts;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 前台订单管理Service实现类
 * Created by macro on 2020/4/6.
 */
@Service
public class PmsPortalProductServiceImpl implements PmsPortalProductService {
    /** “手机”是用户常用的品类搜索词，匹配时只返回手机通讯分类，避免把手机壳等配件混入结果。 */
    private static final String PHONE_SEARCH_KEYWORD = "手机";
    /** 现有商品分类表中的“手机通讯”分类 ID，使用 ID 可避免冗余分类名称未同步导致漏搜。 */
    private static final long PHONE_CATEGORY_ID = 19L;

    @Autowired
    private PmsProductMapper productMapper;
    @Autowired
    private PmsProductCategoryMapper productCategoryMapper;
    @Autowired
    private PmsBrandMapper brandMapper;
    @Autowired
    private PmsProductAttributeMapper productAttributeMapper;
    @Autowired
    private PmsProductAttributeValueMapper productAttributeValueMapper;
    @Autowired
    private PmsSkuStockMapper skuStockMapper;
    @Autowired
    private PmsProductLadderMapper productLadderMapper;
    @Autowired
    private PmsProductFullReductionMapper productFullReductionMapper;
    @Autowired
    private PortalProductDao portalProductDao;
    @Autowired
    private PmsCommentMapper commentMapper;
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private OmsOrderItemMapper orderItemMapper;
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private PortalProductCommentDao productCommentDao;

    @Override
    public List<PmsProduct> search(String keyword, Long brandId, Long productCategoryId, Integer pageNum, Integer pageSize, Integer sort) {
        PageHelper.startPage(pageNum, pageSize);
        PmsProductExample example = new PmsProductExample();
        String searchKeyword = normalizeSearchKeyword(keyword);
        if (StrUtil.isBlank(searchKeyword)) {
            addBaseSearchCriteria(example, brandId, productCategoryId);
        } else {
            // 商品名称未必包含用户输入的品类或品牌，例如“华为 HUAWEI P20”不含“手机”。
            // 为保持原有分页查询，使用多个 OR 条件覆盖商品可检索文本字段。
            String likeKeyword = "%" + searchKeyword + "%";
            boolean phoneOnly = PHONE_SEARCH_KEYWORD.equals(searchKeyword);
            addKeywordSearchCriteria(example, likeKeyword, brandId, productCategoryId, 0, phoneOnly);
            addKeywordSearchCriteria(example, likeKeyword, brandId, productCategoryId, 1, phoneOnly);
            addKeywordSearchCriteria(example, likeKeyword, brandId, productCategoryId, 2, phoneOnly);
            addKeywordSearchCriteria(example, likeKeyword, brandId, productCategoryId, 3, phoneOnly);
            addKeywordSearchCriteria(example, likeKeyword, brandId, productCategoryId, 4, phoneOnly);
            addKeywordSearchCriteria(example, likeKeyword, brandId, productCategoryId, 5, phoneOnly);
        }
        //1->按新品；2->按销量；3->价格从低到高；4->价格从高到低
        int safeSort = sort == null ? 0 : sort;
        if (safeSort == 1) {
            example.setOrderByClause("id desc");
        } else if (safeSort == 2) {
            example.setOrderByClause("sale desc");
        } else if (safeSort == 3) {
            example.setOrderByClause("price asc");
        } else if (safeSort == 4) {
            example.setOrderByClause("price desc");
        }
        return productMapper.selectByExample(example);
    }

    /** 兼容旧版 H5 页面把关键词二次编码后传到接口的情况。 */
    private String normalizeSearchKeyword(String keyword) {
        String normalized = StrUtil.trim(keyword);
        if (StrUtil.isBlank(normalized)) {
            return normalized;
        }
        for (int i = 0; i < 2 && normalized.matches(".*%[0-9a-fA-F]{2}.*"); i++) {
            try {
                String decoded = URLDecoder.decode(normalized, StandardCharsets.UTF_8);
                if (decoded.equals(normalized)) {
                    break;
                }
                normalized = StrUtil.trim(decoded);
            } catch (IllegalArgumentException ex) {
                break;
            }
        }
        return normalized;
    }

    private void addBaseSearchCriteria(PmsProductExample example, Long brandId, Long productCategoryId) {
        PmsProductExample.Criteria criteria = example.createCriteria();
        criteria.andDeleteStatusEqualTo(0).andPublishStatusEqualTo(1);
        if (brandId != null) {
            criteria.andBrandIdEqualTo(brandId);
        }
        if (productCategoryId != null) {
            criteria.andProductCategoryIdEqualTo(productCategoryId);
        }
    }

    /**
     * 为每个可检索字段建立一个 OR 分支，避免把 name、brand_name 等字段错误地 AND 在一起。
     * 字段顺序：name、sub_title、keywords、product_sn、brand_name、product_category_name。
     */
    private void addKeywordSearchCriteria(PmsProductExample example, String likeKeyword,
                                           Long brandId, Long productCategoryId, int field,
                                           boolean phoneOnly) {
        PmsProductExample.Criteria criteria = example.getOredCriteria().isEmpty()
                ? example.createCriteria() : example.or();
        criteria.andDeleteStatusEqualTo(0).andPublishStatusEqualTo(1);
        if (brandId != null) {
            criteria.andBrandIdEqualTo(brandId);
        }
        if (productCategoryId != null) {
            criteria.andProductCategoryIdEqualTo(productCategoryId);
        }
        if (phoneOnly) {
            criteria.andProductCategoryIdEqualTo(PHONE_CATEGORY_ID);
        }
        switch (field) {
            case 0 -> criteria.andNameLike(likeKeyword);
            case 1 -> criteria.andSubTitleLike(likeKeyword);
            case 2 -> criteria.andKeywordsLike(likeKeyword);
            case 3 -> criteria.andProductSnLike(likeKeyword);
            case 4 -> criteria.andBrandNameLike(likeKeyword);
            case 5 -> criteria.andProductCategoryNameLike(likeKeyword);
            default -> throw new IllegalArgumentException("Unsupported product search field: " + field);
        }
    }

    @Override
    public List<PmsProductCategoryNode> categoryTreeList() {
        PmsProductCategoryExample example = new PmsProductCategoryExample();
        List<PmsProductCategory> allList = productCategoryMapper.selectByExample(example);
        List<PmsProductCategoryNode> result = allList.stream()
                .filter(item -> item.getParentId().equals(0L))
                .map(item -> covert(item, allList))
                .collect(Collectors.toList());
        return result;
    }

    @Override
    public PmsPortalProductDetail detail(Long id) {
        PmsPortalProductDetail result = new PmsPortalProductDetail();
        //获取商品信息
        PmsProduct product = getPublishedProduct(id, true);
        result.setProduct(product);
        //获取品牌信息
        PmsBrand brand = brandMapper.selectByPrimaryKey(product.getBrandId());
        result.setBrand(brand);
        //获取商品属性信息
        PmsProductAttributeExample attributeExample = new PmsProductAttributeExample();
        attributeExample.createCriteria().andProductAttributeCategoryIdEqualTo(product.getProductAttributeCategoryId());
        List<PmsProductAttribute> productAttributeList = productAttributeMapper.selectByExample(attributeExample);
        result.setProductAttributeList(productAttributeList);
        //获取商品属性值信息
        if(CollUtil.isNotEmpty(productAttributeList)){
            List<Long> attributeIds = productAttributeList.stream().map(PmsProductAttribute::getId).collect(Collectors.toList());
            PmsProductAttributeValueExample attributeValueExample = new PmsProductAttributeValueExample();
            attributeValueExample.createCriteria().andProductIdEqualTo(product.getId())
                    .andProductAttributeIdIn(attributeIds);
            List<PmsProductAttributeValue> productAttributeValueList = productAttributeValueMapper.selectByExample(attributeValueExample);
            result.setProductAttributeValueList(productAttributeValueList);
        }
        //获取商品SKU库存信息
        PmsSkuStockExample skuExample = new PmsSkuStockExample();
        skuExample.createCriteria().andProductIdEqualTo(product.getId());
        List<PmsSkuStock> skuStockList = skuStockMapper.selectByExample(skuExample);
        result.setSkuStockList(skuStockList);
        //商品阶梯价格设置
        if(product.getPromotionType()==3){
            PmsProductLadderExample ladderExample = new PmsProductLadderExample();
            ladderExample.createCriteria().andProductIdEqualTo(product.getId());
            List<PmsProductLadder> productLadderList = productLadderMapper.selectByExample(ladderExample);
            result.setProductLadderList(productLadderList);
        }
        //商品满减价格设置
        if(product.getPromotionType()==4){
            PmsProductFullReductionExample fullReductionExample = new PmsProductFullReductionExample();
            fullReductionExample.createCriteria().andProductIdEqualTo(product.getId());
            List<PmsProductFullReduction> productFullReductionList = productFullReductionMapper.selectByExample(fullReductionExample);
            result.setProductFullReductionList(productFullReductionList);
        }
        //商品可用优惠券
        result.setCouponList(portalProductDao.getAvailableCouponList(product.getId(),product.getProductCategoryId()));
        return result;
    }

    @Override
    public CommonPage<ProductCommentView> listComments(Long productId, Integer pageNum, Integer pageSize) {
        getPublishedProduct(productId);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long memberId = authentication != null && authentication.getPrincipal() instanceof MemberDetails details
                ? details.getUmsMember().getId() : null;
        PageHelper.startPage(pageNum, pageSize);
        return CommonPage.restPage(productCommentDao.listComments(productId, memberId));
    }

    @Override
    public ProductCommentSummary getCommentSummary(Long productId) {
        getPublishedProduct(productId);
        return productCommentDao.getSummary(productId);
    }

    @Override
    @Transactional(readOnly = true)
    public CommonPage<MyProductCommentView> listMyComments(Integer pageNum, Integer pageSize) {
        Long memberId = memberService.getCurrentMember().getId();
        PageHelper.startPage(pageNum, pageSize);
        return CommonPage.restPage(productCommentDao.listMyComments(memberId));
    }

    @Override
    @Transactional(readOnly = true)
    public CommonPage<ReceivedCommentReplyView> listReceivedReplies(Integer pageNum, Integer pageSize) {
        Long memberId = memberService.getCurrentMember().getId();
        PageHelper.startPage(pageNum, pageSize);
        return CommonPage.restPage(productCommentDao.listReceivedReplies(memberId));
    }

    /** 公开接口只允许访问已发布且未删除的商品。 */
    private PmsProduct getPublishedProduct(Long id) {
        return getPublishedProduct(id, false);
    }

    private PmsProduct getPublishedProduct(Long id, boolean includeDetail) {
        if (id == null) {
            Asserts.fail("商品不存在");
        }
        PmsProductExample example = new PmsProductExample();
        example.createCriteria()
                .andIdEqualTo(id)
                .andDeleteStatusEqualTo(0)
                .andPublishStatusEqualTo(1);
        List<PmsProduct> products = includeDetail
                ? productMapper.selectByExampleWithBLOBs(example)
                : productMapper.selectByExample(example);
        if (products.isEmpty()) {
            Asserts.fail("商品不存在");
        }
        return products.get(0);
    }

    @Override
    @Transactional
    public void createComments(ProductCommentBatchParam param, String clientIp) {
        UmsMember member = memberService.getCurrentMember();
        // 对同一订单的评价串行提交，确保不同订单项并发评价后仍能正确更新订单评价时间。
        OmsOrder order = productCommentDao.lockOrderForComment(param.getOrderId());
        if (order == null || !Objects.equals(member.getId(), order.getMemberId())
                || Integer.valueOf(1).equals(order.getDeleteStatus())) {
            Asserts.fail("订单不存在");
        }
        if (!Integer.valueOf(3).equals(order.getStatus())) {
            Asserts.fail("完成订单后才可以评价商品");
        }

        OmsOrderItemExample itemExample = new OmsOrderItemExample();
        itemExample.createCriteria().andOrderIdEqualTo(order.getId());
        List<OmsOrderItem> orderItems = orderItemMapper.selectByExample(itemExample);
        if (orderItems.isEmpty()) {
            Asserts.fail("订单中没有可评价商品");
        }

        Map<Long, OmsOrderItem> orderItemMap = orderItems.stream()
                .collect(Collectors.toMap(OmsOrderItem::getId, Function.identity()));
        List<Long> requestedItemIds = param.getComments().stream()
                .map(ProductCommentParam::getOrderItemId).toList();
        if (new HashSet<>(requestedItemIds).size() != requestedItemIds.size()) {
            Asserts.fail("同一订单商品不能重复评价");
        }
        if (!orderItemMap.keySet().containsAll(requestedItemIds)) {
            Asserts.fail("评价商品不属于当前订单");
        }
        if (!productCommentDao.selectCommentedOrderItemIds(requestedItemIds).isEmpty()) {
            Asserts.fail("订单中包含已经评价的商品");
        }

        for (ProductCommentParam commentParam : param.getComments()) {
            OmsOrderItem orderItem = orderItemMap.get(commentParam.getOrderItemId());
            if (StrUtil.isBlank(commentParam.getContent())) {
                Asserts.fail("评价内容不能为空");
            }
            PmsComment comment = new PmsComment();
            comment.setProductId(orderItem.getProductId());
            comment.setProductName(orderItem.getProductName());
            comment.setMemberNickName(member.getNickname() == null ? member.getUsername() : member.getNickname());
            comment.setMemberIcon(member.getIcon());
            comment.setStar(commentParam.getStar());
            comment.setContent(commentParam.getContent().trim());
            comment.setProductAttribute(orderItem.getProductAttr());
            comment.setPics(normalizePictures(commentParam.getPics()));
            comment.setMemberIp(clientIp);
            comment.setCreateTime(new Date());
            comment.setShowStatus(1);
            comment.setCollectCouont(0);
            comment.setReadCount(0);
            comment.setReplayCount(0);
            productCommentDao.insert(comment, member.getId(), order.getId(), orderItem.getId());
        }

        if (productCommentDao.countByOrderId(order.getId()) >= orderItems.size()) {
            OmsOrder updateOrder = new OmsOrder();
            updateOrder.setId(order.getId());
            updateOrder.setCommentTime(new Date());
            orderMapper.updateByPrimaryKeySelective(updateOrder);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CommonPage<PmsCommentReplay> listCommentReplies(Long commentId, Integer pageNum, Integer pageSize) {
        PmsComment comment = productCommentDao.selectVisibleComment(commentId);
        if (comment == null) {
            Asserts.fail("评价不存在或已删除");
        }
        getPublishedProduct(comment.getProductId());
        PageHelper.startPage(pageNum, pageSize);
        return CommonPage.restPage(productCommentDao.listReplies(commentId));
    }

    @Override
    @Transactional
    public void createCommentReply(Long commentId, ProductCommentReplyParam param) {
        PmsComment comment = productCommentDao.lockVisibleComment(commentId);
        if (comment == null) {
            Asserts.fail("评价不存在或已删除");
        }
        getPublishedProduct(comment.getProductId());
        if (param == null || StrUtil.isBlank(param.getContent()) || param.getContent().trim().length() > 1000) {
            Asserts.fail("请填写1至1000字的回复内容");
        }
        UmsMember member = memberService.getCurrentMember();
        PmsCommentReplay reply = new PmsCommentReplay();
        reply.setCommentId(commentId);
        reply.setMemberId(member.getId());
        reply.setMemberNickName(member.getNickname() == null ? member.getUsername() : member.getNickname());
        reply.setMemberIcon(member.getIcon());
        reply.setContent(param.getContent().trim());
        reply.setCreateTime(new Date());
        reply.setType(0);
        productCommentDao.insertReply(reply);
        PmsComment update = new PmsComment();
        update.setId(commentId);
        update.setReplayCount(productCommentDao.countReplies(commentId));
        commentMapper.updateByPrimaryKeySelective(update);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteCommentReply(Long replyId) {
        UmsMember member = memberService.getCurrentMember();
        PmsCommentReplay reply = productCommentDao.selectReply(replyId);
        if (reply == null || !Objects.equals(reply.getMemberId(), member.getId())) {
            Asserts.fail("回复不存在或无权删除");
        }
        PmsComment parent = productCommentDao.lockVisibleComment(reply.getCommentId());
        int deleted = productCommentDao.deleteReply(replyId, member.getId());
        if (deleted == 0) {
            Asserts.fail("回复不存在或无权删除");
        }
        if (parent != null) {
            PmsComment update = new PmsComment();
            update.setId(reply.getCommentId());
            update.setReplayCount(productCommentDao.countReplies(reply.getCommentId()));
            commentMapper.updateByPrimaryKeySelective(update);
        }
    }

    @Override
    @Transactional
    public ProductCommentLikeResult toggleCommentLike(Long commentId) {
        PmsComment comment = productCommentDao.lockVisibleComment(commentId);
        if (comment == null) {
            Asserts.fail("评价不存在或已删除");
        }
        getPublishedProduct(comment.getProductId());
        UmsMember member = memberService.getCurrentMember();
        boolean liked = productCommentDao.hasLiked(commentId, member.getId());
        if (liked) {
            productCommentDao.removeLike(commentId, member.getId());
        } else {
            productCommentDao.addLike(commentId, member.getId());
        }
        int count = productCommentDao.countLikes(commentId);
        PmsComment update = new PmsComment();
        update.setId(commentId);
        update.setCollectCouont(count);
        commentMapper.updateByPrimaryKeySelective(update);
        return new ProductCommentLikeResult(!liked, count);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId) {
        UmsMember member = memberService.getCurrentMember();
        PmsComment comment = productCommentDao.lockVisibleComment(commentId);
        if (comment == null || !Objects.equals(comment.getMemberId(), member.getId())) {
            Asserts.fail("评价不存在或无权删除");
        }
        if (productCommentDao.hideComment(commentId, member.getId()) == 0) {
            Asserts.fail("评价不存在或无权删除");
        }
    }

    @Override
    public ProductCommentPurchase findCommentPurchase(Long productId) {
        return productCommentDao.findCommentPurchase(productId, memberService.getCurrentMember().getId());
    }

    @Override
    public List<OmsOrderItem> listUncommentedItems(Long orderId) {
        UmsMember member = memberService.getCurrentMember();
        OmsOrder order = orderMapper.selectByPrimaryKey(orderId);
        if (order == null || !Objects.equals(order.getMemberId(), member.getId())
                || Integer.valueOf(1).equals(order.getDeleteStatus())) {
            Asserts.fail("订单不存在");
        }
        if (!Integer.valueOf(3).equals(order.getStatus())) {
            Asserts.fail("完成订单后才可以评价商品");
        }
        OmsOrderItemExample example = new OmsOrderItemExample();
        example.createCriteria().andOrderIdEqualTo(orderId);
        List<OmsOrderItem> items = orderItemMapper.selectByExample(example);
        if (items.isEmpty()) return items;
        Set<Long> commented = new HashSet<>(productCommentDao.selectCommentedOrderItemIds(
                items.stream().map(OmsOrderItem::getId).toList()));
        return items.stream().filter(item -> !commented.contains(item.getId())).toList();
    }

    private String normalizePictures(String pics) {
        if (StrUtil.isBlank(pics)) {
            return null;
        }
        List<String> urls = Arrays.stream(pics.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        if (urls.size() > 5) {
            Asserts.fail("每件商品最多上传5张评价图片");
        }
        boolean invalid = urls.stream().anyMatch(url ->
                !url.matches("^/uploads/comments/[a-f0-9]{32}\\.(jpg|png)$"));
        if (invalid) {
            Asserts.fail("评价图片地址无效");
        }
        return String.join(",", urls);
    }


    /**
     * 初始对象转化为节点对象
     */
    private PmsProductCategoryNode covert(PmsProductCategory item, List<PmsProductCategory> allList) {
        PmsProductCategoryNode node = new PmsProductCategoryNode();
        BeanUtils.copyProperties(item, node);
        List<PmsProductCategoryNode> children = allList.stream()
                .filter(subItem -> subItem.getParentId().equals(item.getId()))
                .map(subItem -> covert(subItem, allList)).collect(Collectors.toList());
        node.setChildren(children);
        return node;
    }
}
