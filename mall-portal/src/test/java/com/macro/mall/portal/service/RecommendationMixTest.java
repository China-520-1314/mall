package com.macro.mall.portal.service;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.portal.service.impl.HomeServiceImpl;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RecommendationMixTest {
    private List<PmsProduct> products(long... ids) {
        List<PmsProduct> list = new ArrayList<>();
        for(long id:ids) { PmsProduct p=new PmsProduct(); p.setId(id); list.add(p); }
        return list;
    }
    @Test void mixesEqualSlotsWithoutDuplicatesOrLostProducts() {
        var result=HomeServiceImpl.mixRecommendations(products(1,2,3,4,5,6),products(6,5,4,3,2,1));
        assertEquals(List.of(1L,6L,2L,5L,3L,4L),result.stream().map(PmsProduct::getId).toList());
    }
    @Test void weightedQuotaUsesFloorAndHistoricalLevelDoesNotChangeMixContract() {
        Map<Long,Integer> weights = new LinkedHashMap<>(); weights.put(1L, 7); weights.put(2L, 3);
        assertEquals(2, HomeServiceImpl.calculatePersonalizedCount(5, weights, Map.of()));
        assertEquals(0, HomeServiceImpl.calculatePersonalizedCount(5, Map.of(), Map.of()));
    }
    @Test void handlesColdStartIdenticalRanksAndSmallCatalog() {
        assertEquals(3,HomeServiceImpl.mixRecommendations(products(1,2,3),products(1,2,3)).size());
        assertTrue(HomeServiceImpl.mixRecommendations(List.of(),List.of()).isEmpty());
    }
}
