package com.macro.mall.portal.service.impl;

import com.github.pagehelper.PageHelper;
import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.dao.HomeDao;
import com.macro.mall.portal.domain.FlashPromotionProduct;
import com.macro.mall.portal.domain.HomeContentResult;
import com.macro.mall.portal.domain.HomeFlashPromotion;
import com.macro.mall.portal.domain.MemberReadHistory;
import com.macro.mall.portal.domain.MemberProductCollection;
import com.macro.mall.portal.repository.MemberReadHistoryRepository;
import com.macro.mall.portal.repository.MemberProductCollectionRepository;
import com.macro.mall.portal.service.HomeService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.util.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.domain.PageRequest;

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
    private OmsOrderMapper orderMapper;
    @Autowired
    private OmsOrderItemMapper orderItemMapper;
    @Autowired
    private OmsCartItemMapper cartItemMapper;
    @Autowired
    private MemberReadHistoryRepository readHistoryRepository;
    @Autowired
    private MemberProductCollectionRepository collectionRepository;
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private PmsProductCategoryMapper productCategoryMapper;
    @Autowired
    private CmsSubjectMapper subjectMapper;

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
        if (pageSize == null || pageSize < 1 || pageSize > 50 || pageNum == null || pageNum < 1) {
            throw new IllegalArgumentException("推荐分页参数无效，每页须为1至50条");
        }
        PmsProductExample example = new PmsProductExample();
        example.createCriteria()
                .andDeleteStatusEqualTo(0)
                .andPublishStatusEqualTo(1);
        List<PmsProduct> products = new ArrayList<>(productMapper.selectByExample(example));
        List<PmsProduct> discovery = new ArrayList<>(products);
        discovery.sort(Comparator.comparing(PmsProduct::getId).reversed());
        Map<Long, Integer> category = new HashMap<>(), brand = new HashMap<>(), productPreference = new HashMap<>();
        try {
            Long memberId = memberService.getCurrentMember().getId();
            OmsCartItemExample cartExample = new OmsCartItemExample();
            cartExample.createCriteria().andMemberIdEqualTo(memberId).andDeleteStatusEqualTo(0);
            cartItemMapper.selectByExample(cartExample).forEach(i -> addPreference(products, i.getProductId(), category, brand, productPreference, 4));
            List<MemberReadHistory> reads = readHistoryRepository.findByMemberIdOrderByCreateTimeDesc(memberId, PageRequest.of(0, 100)).getContent();
            List<MemberProductCollection> collections = collectionRepository.findByMemberId(memberId, PageRequest.of(0, 100)).getContent();
            OmsOrderExample orderExample = new OmsOrderExample();
            orderExample.createCriteria().andMemberIdEqualTo(memberId).andStatusEqualTo(3);
            List<Long> completedOrderIds = orderMapper.selectByExample(orderExample).stream().map(OmsOrder::getId).toList();
            if (!completedOrderIds.isEmpty()) {
                OmsOrderItemExample itemExample = new OmsOrderItemExample();
                itemExample.createCriteria().andOrderIdIn(completedOrderIds);
                orderItemMapper.selectByExample(itemExample).forEach(i -> addPreference(products, i.getProductId(), category, brand, productPreference, 10));
            }
            for (int i = 0; i < reads.size(); i++) {
                // 历史行为默认比本次行为低一级，避免旧兴趣长期压过近期兴趣。
                addPreference(products, reads.get(i).getProductId(), category, brand, productPreference,
                        Math.max(1, 4 - i / 20));
            }
            for (int i = 0; i < collections.size(); i++) {
                addPreference(products, collections.get(i).getProductId(), category, brand, productPreference,
                        Math.max(2, 8 - i / 20));
            }
            products.sort(Comparator.comparingDouble((PmsProduct p) -> score(p, category, brand, productPreference)).reversed()
                    .thenComparing(PmsProduct::getId));

        } catch (Exception ignored) {
            products.sort(Comparator.comparingInt((PmsProduct p) -> Math.max(0, p.getSale() == null ? 0 : p.getSale())).reversed()
                    .thenComparing(PmsProduct::getId));
        }
        List<PmsProduct> personalized = products.stream()
                .filter(p -> productPreference.containsKey(p.getId())
                        || category.containsKey(p.getProductCategoryId())
                        || brand.containsKey(p.getBrandId()))
                .toList();
        int personalizedCount = calculatePersonalizedCount(pageSize, category, productPreference);
        List<PmsProduct> mixed = mixRecommendations(personalized, diversify(discovery), personalizedCount);
        products.clear();
        products.addAll(mixed);
        long offset = (long) (pageNum - 1) * pageSize;
        if (offset >= products.size()) return List.of();
        int from = (int) offset;
        return from >= products.size() ? List.of() : new ArrayList<>(products.subList(from, Math.min(products.size(), from + pageSize)));
    }

    private void addPreference(List<PmsProduct> products, Long productId, Map<Long, Integer> category, Map<Long, Integer> brand, Map<Long, Integer> productPreference, int weight) {
        products.stream().filter(p -> p.getId().equals(productId)).findFirst().ifPresent(p -> {
            productPreference.merge(productId, weight, Integer::sum);
            if (p.getProductCategoryId() != null) category.merge(p.getProductCategoryId(), weight, Integer::sum);
            if (p.getBrandId() != null) brand.merge(p.getBrandId(), weight, Integer::sum);
        });
    }

    private int productPreferenceScore(PmsProduct p, Map<Long, Integer> category, Map<Long, Integer> brand, Map<Long, Integer> productPreference) {
        return (int) Math.round(score(p, category, brand, productPreference));
    }

    /**
     * 依据各类目加权后的数字化占比决定首页个性化槽位；结果始终向下取整。
     * 采用总偏好权重 / (总偏好权重 + 10) 归一化，避免冷启动时占满首页。
     */
    public static int calculatePersonalizedCount(int pageSize, Map<Long, Integer> categoryWeights, Map<Long, Integer> productWeights) {
        if (pageSize <= 0 || categoryWeights == null || categoryWeights.isEmpty()) return 0;
        int total = categoryWeights.values().stream().filter(Objects::nonNull).mapToInt(v -> Math.max(0, v)).sum();
        if (total <= 0) return 0;
        int count = (int) Math.floor(pageSize * (total / (double) (total + 10)));
        return Math.max(1, Math.min(pageSize, count));
    }

    private double score(PmsProduct p, Map<Long, Integer> category, Map<Long, Integer> brand, Map<Long, Integer> productPreference) {
        return productPreference.getOrDefault(p.getId(), 0) * 3D
                + category.getOrDefault(p.getProductCategoryId(), 0) * 10D
                + brand.getOrDefault(p.getBrandId(), 0) * 4D
                + Math.min(p.getSale() == null ? 0 : p.getSale(), 100000) * 0.01D
                + (p.getRecommandStatus() != null && p.getRecommandStatus() == 1 ? 2D : 0D)
                + (p.getNewStatus() != null && p.getNewStatus() == 1 ? 1D : 0D);
    }

    /** Mixes exactly the requested number of personalized items into each page-sized window. */
    public static List<PmsProduct> mixRecommendations(List<PmsProduct> ranked, List<PmsProduct> discovery, int personalizedCount) {
        List<PmsProduct> result = new ArrayList<>(); Set<Long> used = new HashSet<>();
        int r=0,d=0; int slots=Math.max(0,personalizedCount); int discoverySlots=Math.max(0,Math.max(ranked.size(), discovery.size())-slots);
        while (r<ranked.size() || d<discovery.size()) {
            if (slots>0 && r<ranked.size()) { PmsProduct p=ranked.get(r++); if(used.add(p.getId())) {result.add(p);slots--;} continue; }
            if (d<discovery.size()) { PmsProduct p=discovery.get(d++); if(used.add(p.getId())) {result.add(p);discoverySlots--;} }
            else if (r<ranked.size()) { PmsProduct p=ranked.get(r++); if(used.add(p.getId())) result.add(p); }
        }
        return result;
    }

    /** Backward-compatible 50/50 mix for callers without a calculated quota. */
    public static List<PmsProduct> mixRecommendations(List<PmsProduct> ranked, List<PmsProduct> discovery) {
        List<PmsProduct> result = new ArrayList<>(); Set<Long> used = new HashSet<>(); int r=0,d=0;
        while (r<ranked.size() || d<discovery.size()) {
            while(r<ranked.size() && used.contains(ranked.get(r).getId())) r++;
            if(r<ranked.size()){PmsProduct p=ranked.get(r++);used.add(p.getId());result.add(p);}
            while(d<discovery.size() && used.contains(discovery.get(d).getId())) d++;
            if(d<discovery.size()){PmsProduct p=discovery.get(d++);used.add(p.getId());result.add(p);}
        }
        return result;
    }

    private List<PmsProduct> diversify(List<PmsProduct> ranked) {
        Map<Long, java.util.ArrayDeque<PmsProduct>> groups = new java.util.LinkedHashMap<>();
        for (PmsProduct p : ranked) groups.computeIfAbsent(p.getProductCategoryId(), k -> new java.util.ArrayDeque<>()).add(p);
        List<PmsProduct> result = new ArrayList<>();
        while (result.size() < ranked.size()) {
            for (java.util.ArrayDeque<PmsProduct> group : groups.values()) {
                if (!group.isEmpty()) result.add(group.removeFirst());
            }
        }
        return result;
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
