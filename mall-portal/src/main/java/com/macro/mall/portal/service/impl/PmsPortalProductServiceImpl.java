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
import com.macro.mall.portal.service.PmsPortalProductService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.exception.Asserts;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 前台订单管理Service实现类
 * Created by macro on 2020/4/6.
 */
@Service
public class PmsPortalProductServiceImpl implements PmsPortalProductService {
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
        PmsProductExample.Criteria criteria = example.createCriteria();
        criteria.andDeleteStatusEqualTo(0);
        criteria.andPublishStatusEqualTo(1);
        if (StrUtil.isNotEmpty(keyword)) {
            criteria.andNameLike("%" + keyword + "%");
        }
        if (brandId != null) {
            criteria.andBrandIdEqualTo(brandId);
        }
        if (productCategoryId != null) {
            criteria.andProductCategoryIdEqualTo(productCategoryId);
        }
        //1->按新品；2->按销量；3->价格从低到高；4->价格从高到低
        if (sort == 1) {
            example.setOrderByClause("id desc");
        } else if (sort == 2) {
            example.setOrderByClause("sale desc");
        } else if (sort == 3) {
            example.setOrderByClause("price asc");
        } else if (sort == 4) {
            example.setOrderByClause("price desc");
        }
        return productMapper.selectByExample(example);
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
        PmsProduct product = getPublishedProduct(id);
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
    public CommonPage<PmsComment> listComments(Long productId, Integer pageNum, Integer pageSize) {
        getPublishedProduct(productId);
        PageHelper.startPage(pageNum, pageSize);
        PmsCommentExample example = new PmsCommentExample();
        example.createCriteria().andProductIdEqualTo(productId).andShowStatusEqualTo(1);
        example.setOrderByClause("create_time desc");
        return CommonPage.restPage(commentMapper.selectByExampleWithBLOBs(example));
    }

    @Override
    public ProductCommentSummary getCommentSummary(Long productId) {
        getPublishedProduct(productId);
        return productCommentDao.getSummary(productId);
    }

    /** 公开接口只允许访问已发布且未删除的商品。 */
    private PmsProduct getPublishedProduct(Long id) {
        if (id == null) {
            Asserts.fail("商品不存在");
        }
        PmsProductExample example = new PmsProductExample();
        example.createCriteria()
                .andIdEqualTo(id)
                .andDeleteStatusEqualTo(0)
                .andPublishStatusEqualTo(1);
        List<PmsProduct> products = productMapper.selectByExample(example);
        if (products.isEmpty()) {
            Asserts.fail("商品不存在");
        }
        return products.get(0);
    }

    @Override
    @Transactional
    public void createComments(ProductCommentBatchParam param, String clientIp) {
        UmsMember member = memberService.getCurrentMember();
        OmsOrder order = orderMapper.selectByPrimaryKey(param.getOrderId());
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
