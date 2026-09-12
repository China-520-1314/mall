<template>
  <view class="home-page">
    <view class="home-header">
      <view class="search-row">
        <view class="search-box" role="button" @click="handleSearch">
          <text class="yticon icon-sousuo"></text>
          <text>搜索商品、品牌或分类</text>
        </view>
        <view class="notice-button" role="button" @click="navigate('/pages/notice/notice')">
          <text class="yticon icon-icon--"></text>
          <text>消息</text>
        </view>
      </view>
      <view class="channel-nav">
        <view v-for="channel in channels" :key="channel.label" class="channel" role="button" @click="navigate(channel.url)">
          <view class="channel-icon" :class="channel.color">
            <image :src="channel.icon" mode="aspectFit" />
          </view>
          <text class="channel-label">{{ channel.label }}</text>
        </view>
      </view>
    </view>

    <view class="recommend-heading">
      <text class="heading-title">为你推荐</text>
      <text class="heading-tip">发现适合你的好物</text>
    </view>
    <view class="recommend-grid">
      <view v-for="item in recommendProductList" :key="item.id" class="product-card" @click="navigate('/pages/product/product?id=' + item.id)">
        <view class="product-image">
          <image :src="item.pic" mode="aspectFit" lazy-load />
        </view>
        <view class="product-info">
          <text class="product-name">{{ item.name }}</text>
          <text v-if="item.subTitle" class="product-subtitle">{{ item.subTitle }}</text>
          <view class="product-meta">
            <text v-if="item.productCategoryName" class="category-label">{{ item.productCategoryName }}</text>
            <text v-if="item.brandName" class="brand-label">{{ item.brandName }}</text>
          </view>
          <view class="product-bottom">
            <view class="price"><text class="currency">¥</text>{{ formatPrice(item.price) }}</view>
            <text class="sales">已售 {{ item.sale || 0 }}</text>
          </view>
        </view>
      </view>
    </view>
    <view v-if="loadError" class="list-state" @click="loadRecommendations(recommendProductList.length === 0)">
      加载失败，点击重试
    </view>
    <view v-else-if="!loading && recommendProductList.length === 0" class="list-state">暂无推荐商品</view>
    <uni-load-more v-else :status="loading ? 'loading' : hasMore ? 'more' : 'noMore'" />
    <assistant-pet />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { getRecommendProductListAPI } from '@/apis/home'
import { useMemberStore } from '@/stores/member'
import type { PmsProduct } from '@/types/product'
import AssistantPet from '@/components/assistant-pet.vue'

const channels = [
  { label: '品牌制造商直供', icon: '/static/icon_home_brand.png', color: 'brand', url: '/pages/brand/list' },
  { label: '秒杀专区', icon: '/static/icon_flash_promotion.png', color: 'flash', url: '/pages/product/flashProductList' },
  { label: '新鲜好物', icon: '/static/icon_new_product.png', color: 'fresh', url: '/pages/product/newProductList' },
  { label: '人气推荐', icon: '/static/icon_hot_product.png', color: 'hot', url: '/pages/product/hotProductList' },
]
const memberStore = useMemberStore()
const recommendProductList = ref<PmsProduct[]>([])
const loading = ref(false)
const hasMore = ref(true)
const loadError = ref(false)
const pageSize = 8
let pageNum = 0
let requestVersion = 0
let loadedMemberId = memberStore.memberInfo?.id

const navigate = (url: string) => uni.navigateTo({ url })
const handleSearch = () => navigate('/pages/product/search')
const formatPrice = (value: number) => Number(value || 0).toFixed(2).replace(/\.00$/, '')

const loadRecommendations = async (refresh = false) => {
  if (!refresh && (loading.value || !hasMore.value)) return
  const version = ++requestVersion
  const nextPage = refresh ? 1 : pageNum + 1
  loading.value = true
  loadError.value = false
  try {
    const res = await getRecommendProductListAPI({ pageNum: nextPage, pageSize })
    if (version !== requestVersion) return
    const list = res.data || []
    const previous = refresh ? [] : recommendProductList.value
    const seen = new Set(previous.map((item) => item.id))
    recommendProductList.value = previous.concat(list.filter((item) => !seen.has(item.id)))
    pageNum = nextPage
    hasMore.value = list.length === pageSize
  } catch {
    if (version === requestVersion) loadError.value = true
  } finally {
    if (version === requestVersion) {
      loading.value = false
      uni.stopPullDownRefresh()
    }
  }
}
onShow(() => {
  const memberId = memberStore.memberInfo?.id
  if (loadedMemberId !== memberId) {
    loadedMemberId = memberId
    recommendProductList.value = []
  }
  // 首页是缓存的 tab 页面，返回时也要重新获取最新推荐；首次进入由此统一加载。
  loadRecommendations(true)
})
onPullDownRefresh(() => loadRecommendations(true))
onReachBottom(() => loadRecommendations())
</script>

<style lang="scss">
page { background: #f4f5f7; }
</style>
<style lang="scss" scoped>
.home-page { min-height: 100vh; max-width: 1200px; margin: 0 auto; padding-bottom: 24rpx; }
.home-header { background: #fff; padding: calc(var(--status-bar-height) + 22rpx) 24rpx 26rpx; }
.search-row { display: flex; align-items: center; gap: 22rpx; }
.search-box { flex: 1; min-width: 0; display: flex; align-items: center; gap: 16rpx; background: #f2f3f5; color: #8a8d93; height: 76rpx; border-radius: 18rpx; padding: 0 26rpx; font-size: 28rpx; }
.notice-button { display: flex; flex-direction: column; align-items: center; gap: 4rpx; color: #52555b; font-size: 20rpx; }
.notice-button .yticon { font-size: 34rpx; }
.channel-nav { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10rpx; padding-top: 30rpx; }
.channel { display: flex; flex-direction: column; align-items: center; text-align: center; }
.channel-icon { display: flex; justify-content: center; align-items: center; width: 96rpx; height: 96rpx; border-radius: 26rpx; margin-bottom: 12rpx; }
.channel-icon image { width: 70rpx; height: 70rpx; }
.brand { background: #fff1e5; } .flash { background: #ffe9e9; } .fresh { background: #e7f8ef; } .hot { background: #fff0ef; }
.channel-label { font-size: 23rpx; color: #303133; line-height: 36rpx; }
.recommend-heading { display: flex; align-items: baseline; gap: 16rpx; padding: 26rpx 24rpx 20rpx; }
.heading-title { color: #e73a42; font-size: 34rpx; font-weight: 700; }
.heading-tip { color: #93969c; font-size: 23rpx; }
.recommend-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16rpx; padding: 0 16rpx; }
.product-card { min-width: 0; background: #fff; border-radius: 14rpx; overflow: hidden; }
.product-image { width: 100%; aspect-ratio: 1; background: #fff; }
.product-image image { display: block; width: 100%; height: 100%; }
.product-info { padding: 16rpx; }
.product-name { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; font-size: 28rpx; line-height: 40rpx; min-height: 80rpx; color: #25282d; overflow-wrap: anywhere; }
.product-subtitle { display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; color: #8a8d93; font-size: 22rpx; margin-top: 8rpx; }
.product-meta { display: flex; gap: 8rpx; align-items: center; overflow: hidden; height: 52rpx; }
.category-label { color: #e23f48; background: #fff0f1; border-radius: 4rpx; padding: 2rpx 8rpx; font-size: 20rpx; white-space: nowrap; }
.brand-label { color: #8a8d93; font-size: 20rpx; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.product-bottom { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: baseline; gap: 4rpx; margin-top: 8rpx; }
.price { color: #e62e38; font-weight: 700; font-size: 36rpx; }
.currency { font-size: 24rpx; margin-right: 3rpx; }
.sales { color: #999da4; font-size: 21rpx; }
.list-state { padding: 50rpx 24rpx; text-align: center; font-size: 26rpx; color: #999; }
@media (min-width: 900px) {
  .channel-label { font-size: 14px; }
  .product-image { height: 360px; aspect-ratio: auto; }
  .product-info { padding: 16px; }
  .product-name { font-size: 18px; line-height: 26px; min-height: 52px; }
  .product-subtitle { font-size: 13px; }
  .price { font-size: 24px; }
}
</style>
