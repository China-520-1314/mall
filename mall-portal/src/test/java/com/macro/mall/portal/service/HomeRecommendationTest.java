package com.macro.mall.portal.service;

import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.portal.dao.HomeDao;
import com.macro.mall.portal.domain.MemberReadHistory;
import com.macro.mall.portal.repository.MemberProductCollectionRepository;
import com.macro.mall.portal.repository.MemberReadHistoryRepository;
import com.macro.mall.portal.service.impl.HomeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HomeRecommendationTest {
    private final PmsProductMapper products = mock(PmsProductMapper.class);
    private final HomeDao dao = mock(HomeDao.class);
    private final MemberReadHistoryRepository histories = mock(MemberReadHistoryRepository.class);
    private final MemberProductCollectionRepository collections = mock(MemberProductCollectionRepository.class);
    private final HomeServiceImpl service = new HomeServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "productMapper", products);
        ReflectionTestUtils.setField(service, "homeDao", dao);
        ReflectionTestUtils.setField(service, "readHistoryRepository", histories);
        ReflectionTestUtils.setField(service, "collectionRepository", collections);
        // All candidates share Xiaomi's brand; the preference must distinguish categories.
        when(products.selectByExample(any())).thenAnswer(call -> new ArrayList<>(List.of(
                product(1, 19, 20), product(2, 38, 10), product(3, 38, 0))));
    }

    @Test
    void newFridgeParticipatesInCartRecommendationsWithoutMongo() {
        when(dao.getCartPreferenceProductIds(7L)).thenReturn(List.of(2L));
        when(histories.findTop30ByMemberIdOrderByCreateTimeDesc(7L))
                .thenThrow(new DataAccessResourceFailureException("Mongo offline"));
        assertEquals(List.of(2L, 3L, 1L), ids(service.personalizedProductList(7L, 8, 1)));
        verify(dao).getPurchasedPreferenceProductIds(7L);
        verify(products, never()).selectByPrimaryKey(any());
    }

    @Test
    void paidOrdersInfluenceCategoryRanking() {
        when(dao.getPurchasedPreferenceProductIds(7L)).thenReturn(List.of(2L));
        assertEquals(List.of(2L, 3L, 1L), ids(service.personalizedProductList(7L, 8, 1)));
    }

    @Test
    void availableBrowsingHistoryStillInfluencesRanking() {
        MemberReadHistory history = new MemberReadHistory();
        history.setProductId(3L);
        when(histories.findTop30ByMemberIdOrderByCreateTimeDesc(7L)).thenReturn(List.of(history));
        assertEquals(List.of(2L, 3L, 1L), ids(service.personalizedProductList(7L, 8, 1)));
    }

    @Test
    void noHistoryFallsBackToPopularityAndKeepsPagination() {
        when(histories.findTop30ByMemberIdOrderByCreateTimeDesc(7L))
                .thenThrow(new DataAccessResourceFailureException("Mongo offline"));
        assertEquals(List.of(3L), ids(service.personalizedProductList(7L, 2, 2)));
        assertEquals(List.of(), ids(service.personalizedProductList(7L, 2, 3)));
    }

    @Test
    void anonymousVisitorDoesNotQueryMemberBehavior() {
        assertEquals(List.of(1L, 2L, 3L), ids(service.personalizedProductList(null, 8, 1)));
        verifyNoInteractions(dao, histories, collections);
    }

    private PmsProduct product(long id, long category, int sales) {
        PmsProduct p = new PmsProduct();
        p.setId(id);
        p.setProductCategoryId(category);
        p.setBrandId(6L);
        p.setSale(sales);
        return p;
    }

    private List<Long> ids(List<PmsProduct> items) {
        return items.stream().map(PmsProduct::getId).toList();
    }
}
