package com.macro.mall.portal.service.impl;

import com.github.pagehelper.PageHelper;
import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.dao.HomeDao;
import com.macro.mall.portal.domain.FlashPromotionProduct;
import com.macro.mall.portal.domain.HomeContentResult;
import com.macro.mall.portal.domain.HomeFlashPromotion;
import com.macro.mall.portal.domain.MemberProductCollection;
import com.macro.mall.portal.domain.MemberReadHistory;
import com.macro.mall.portal.repository.MemberProductCollectionRepository;
import com.macro.mall.portal.repository.MemberReadHistoryRepository;
import com.macro.mall.portal.service.HomeService;
import com.macro.mall.portal.util.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页内容管理Service实现类
 * Created by macro on 2019/1/28.
 */
@Service
public class HomeServiceImpl implements HomeService {
    @Autowired
    private SmsHomeAdvertiseMapper advertiseMapper;
    @Autowired
    private HomeDao homeDao;
    @Autowired
    private SmsFlashPromotionMapper flashPromotionMapper;
    @Autowired
    private SmsFlashPromotionSessionMapper promotionSessionMapper;
    @Autowired
    private PmsProductMapper productMapper;
    @Autowired
    private PmsProductCategoryMapper productCategoryMapper;
    @Autowired
    private CmsSubjectMapper subjectMapper;
    @Autowired
    private MemberReadHistoryRepository readHistoryRepository;
    @Autowired
    private MemberProductCollectionRepository collectionRepository;

    @Override
    public HomeContentResult content() {
        HomeContentResult result = new HomeContentResult();
        //获取首页广告
        result.setAdvertiseList(getHomeAdvertiseList());
        //获取推荐品牌
        result.setBrandList(homeDao.getRecommendBrandList(0,6));
        //获取秒杀信息
        result.setHomeFlashPromotion(getHomeFlashPromotion());
        //获取新品推荐
        result.setNewProductList(homeDao.getNewProductList(0,4));
        //获取人气推荐
        result.setHotProductList(homeDao.getHotProductList(0,4));
        //获取推荐专题
        result.setSubjectList(homeDao.getRecommendSubjectList(0,4));
        return result;
    }

    @Override
    public List<PmsProduct> recommendProductList(Integer pageSize, Integer pageNum) {
        // TODO: 2019/1/29 暂时默认推荐所有商品
        PageHelper.startPage(pageNum,pageSize);
        PmsProductExample example = new PmsProductExample();
        example.createCriteria()
                .andDeleteStatusEqualTo(0)
                .andPublishStatusEqualTo(1);
        return productMapper.selectByExample(example);
    }

    @Override
    public List<PmsProduct> personalizedProductList(Long memberId, Integer pageSize, Integer pageNum) {
        int safePageSize = pageSize == null ? 8 : Math.min(Math.max(pageSize, 1), 40);
        int safePageNum = pageNum == null ? 1 : Math.max(pageNum, 1);

        PmsProductExample example = new PmsProductExample();
        example.createCriteria().andDeleteStatusEqualTo(0).andPublishStatusEqualTo(1);
        List<PmsProduct> products = productMapper.selectByExample(example);
        if (products.isEmpty()) {
            return products;
        }

        // 未登录用户使用可解释、稳定的热门+新品兜底推荐。
        if (memberId == null) {
            products.sort(Comparator.comparing(PmsProduct::getSale, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(PmsProduct::getId));
            return slice(products, safePageNum, safePageSize);
        }

        Map<Long, Integer> categoryWeights = new HashMap<>();
        Map<Long, Integer> brandWeights = new HashMap<>();
        for (MemberReadHistory history : readHistoryRepository.findTop30ByMemberIdOrderByCreateTimeDesc(memberId)) {
            addPreference(history == null ? null : history.getProductId(), 2, categoryWeights, brandWeights);
        }
        for (MemberProductCollection collection : collectionRepository.findTop30ByMemberIdOrderByCreateTimeDesc(memberId)) {
            addPreference(collection == null ? null : collection.getProductId(), 5, categoryWeights, brandWeights);
        }

        // 没有行为数据时直接给新用户返回热门商品。
        if (categoryWeights.isEmpty() && brandWeights.isEmpty()) {
            products.sort(Comparator.comparing(PmsProduct::getSale, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(PmsProduct::getId));
            return slice(products, safePageNum, safePageSize);
        }

        products.sort(Comparator.comparingDouble((PmsProduct product) -> recommendationScore(product, categoryWeights, brandWeights))
                .reversed()
                .thenComparing(PmsProduct::getId));
        return slice(products, safePageNum, safePageSize);
    }

    private void addPreference(Long productId, int weight,
                               Map<Long, Integer> categoryWeights, Map<Long, Integer> brandWeights) {
        if (productId == null) {
            return;
        }
        PmsProduct product = productMapper.selectByPrimaryKey(productId);
        if (product == null) {
            return;
        }
        if (product.getProductCategoryId() != null) {
            categoryWeights.merge(product.getProductCategoryId(), weight, Integer::sum);
        }
        if (product.getBrandId() != null) {
            brandWeights.merge(product.getBrandId(), weight, Integer::sum);
        }
    }

    private double recommendationScore(PmsProduct product, Map<Long, Integer> categoryWeights,
                                        Map<Long, Integer> brandWeights) {
        double score = 0;
        score += categoryWeights.getOrDefault(product.getProductCategoryId(), 0) * 10D;
        score += brandWeights.getOrDefault(product.getBrandId(), 0) * 6D;
        score += product.getSale() == null ? 0 : Math.min(product.getSale(), 10000) * 0.01D;
        return score;
    }

    private List<PmsProduct> slice(List<PmsProduct> products, int pageNum, int pageSize) {
        int from = Math.min((pageNum - 1) * pageSize, products.size());
        int to = Math.min(from + pageSize, products.size());
        return new ArrayList<>(products.subList(from, to));
    }

    @Override
    public List<PmsProductCategory> getProductCateList(Long parentId) {
        PmsProductCategoryExample example = new PmsProductCategoryExample();
        example.createCriteria()
                .andShowStatusEqualTo(1)
                .andParentIdEqualTo(parentId);
        example.setOrderByClause("sort desc");
        return productCategoryMapper.selectByExample(example);
    }

    @Override
    public List<CmsSubject> getSubjectList(Long cateId, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum,pageSize);
        CmsSubjectExample example = new CmsSubjectExample();
        CmsSubjectExample.Criteria criteria = example.createCriteria();
        criteria.andShowStatusEqualTo(1);
        if(cateId!=null){
            criteria.andCategoryIdEqualTo(cateId);
        }
        return subjectMapper.selectByExample(example);
    }

    @Override
    public List<PmsProduct> hotProductList(Integer pageNum, Integer pageSize) {
        int offset = pageSize * (pageNum - 1);
        return homeDao.getHotProductList(offset, pageSize);
    }

    @Override
    public List<PmsProduct> newProductList(Integer pageNum, Integer pageSize) {
        int offset = pageSize * (pageNum - 1);
        return homeDao.getNewProductList(offset, pageSize);
    }

    private HomeFlashPromotion getHomeFlashPromotion() {
        HomeFlashPromotion homeFlashPromotion = new HomeFlashPromotion();
        //获取当前秒杀活动
        Date now = new Date();
        SmsFlashPromotion flashPromotion = getFlashPromotion(now);
        if (flashPromotion != null) {
            //获取当前秒杀场次
            SmsFlashPromotionSession flashPromotionSession = getFlashPromotionSession(now);
            if (flashPromotionSession != null) {
                homeFlashPromotion.setStartTime(flashPromotionSession.getStartTime());
                homeFlashPromotion.setEndTime(flashPromotionSession.getEndTime());
                //获取下一个秒杀场次
                SmsFlashPromotionSession nextSession = getNextFlashPromotionSession(homeFlashPromotion.getStartTime());
                if(nextSession!=null){
                    homeFlashPromotion.setNextStartTime(nextSession.getStartTime());
                    homeFlashPromotion.setNextEndTime(nextSession.getEndTime());
                }
                //获取秒杀商品
                List<FlashPromotionProduct> flashProductList = homeDao.getFlashProductList(flashPromotion.getId(), flashPromotionSession.getId());
                homeFlashPromotion.setProductList(flashProductList);
            }
        }
        return homeFlashPromotion;
    }

    //获取下一个场次信息
    private SmsFlashPromotionSession getNextFlashPromotionSession(Date date) {
        SmsFlashPromotionSessionExample sessionExample = new SmsFlashPromotionSessionExample();
        sessionExample.createCriteria()
                .andStartTimeGreaterThan(date);
        sessionExample.setOrderByClause("start_time asc");
        List<SmsFlashPromotionSession> promotionSessionList = promotionSessionMapper.selectByExample(sessionExample);
        if (!CollectionUtils.isEmpty(promotionSessionList)) {
            return promotionSessionList.get(0);
        }
        return null;
    }

    private List<SmsHomeAdvertise> getHomeAdvertiseList() {
        SmsHomeAdvertiseExample example = new SmsHomeAdvertiseExample();
        example.createCriteria().andTypeEqualTo(1).andStatusEqualTo(1);
        example.setOrderByClause("sort desc");
        return advertiseMapper.selectByExample(example);
    }

    //根据时间获取秒杀活动
    private SmsFlashPromotion getFlashPromotion(Date date) {
        Date currDate = DateUtil.getDate(date);
        SmsFlashPromotionExample example = new SmsFlashPromotionExample();
        example.createCriteria()
                .andStatusEqualTo(1)
                .andStartDateLessThanOrEqualTo(currDate)
                .andEndDateGreaterThanOrEqualTo(currDate);
        List<SmsFlashPromotion> flashPromotionList = flashPromotionMapper.selectByExample(example);
        if (!CollectionUtils.isEmpty(flashPromotionList)) {
            return flashPromotionList.get(0);
        }
        return null;
    }

    //根据时间获取秒杀场次
    private SmsFlashPromotionSession getFlashPromotionSession(Date date) {
        Date currTime = DateUtil.getTime(date);
        SmsFlashPromotionSessionExample sessionExample = new SmsFlashPromotionSessionExample();
        sessionExample.createCriteria()
                .andStartTimeLessThanOrEqualTo(currTime)
                .andEndTimeGreaterThanOrEqualTo(currTime);
        List<SmsFlashPromotionSession> promotionSessionList = promotionSessionMapper.selectByExample(sessionExample);
        if (!CollectionUtils.isEmpty(promotionSessionList)) {
            return promotionSessionList.get(0);
        }
        return null;
    }
}
