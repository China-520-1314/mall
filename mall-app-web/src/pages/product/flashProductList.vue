<template>
  <view class="flash-page">
    <view class="flash-heading">
      <text class="title">秒杀专区</text>
      <text v-if="activeProducts.length">本场结束还剩 {{ remaining }}</text>
      <text v-else>限时好物，敬请期待</text>
    </view>
    <view v-if="loading" class="empty">正在加载活动…</view>
    <view v-else-if="failed" class="empty" @click="loadData">加载失败，点击重试</view>
    <view v-else-if="!activeProducts.length" class="empty">
      <text>当前暂无进行中的秒杀活动</text>
      <button size="mini" @click="goHome">去看看为你推荐</button>
    </view>
    <view v-else class="product-list">
      <view v-for="item in activeProducts" :key="item.id" class="product" @click="openProduct(item.id)">
        <image :src="item.pic" mode="aspectFit" />
        <view class="product-info">
          <text class="name">{{ item.name }}</text>
          <text class="subtitle">{{ item.subTitle }}</text>
          <text class="price">¥{{ item.flashPromotionPrice ?? item.price }}</text>
          <text class="detail-link">查看商品 ›</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onPullDownRefresh, onUnload } from '@dcloudio/uni-app'
import { getHomeContentAPI } from '@/apis/home'
import type { HomeFlashPromotion } from '@/types/home'

const promotion = ref<HomeFlashPromotion | null>(null)
const loading = ref(false)
const failed = ref(false)
const now = ref(Date.now())
let timer: ReturnType<typeof setInterval> | undefined
const endTime = computed(() => {
  if (!promotion.value?.endTime) return 0
  const source = new Date(promotion.value.endTime)
  if (!Number.isFinite(source.getTime())) return 0
  const end = new Date(now.value)
  end.setHours(source.getHours(), source.getMinutes(), source.getSeconds(), 0)
  return end.getTime()
})
const activeProducts = computed(() => endTime.value > now.value ? promotion.value?.productList || [] : [])
const remaining = computed(() => {
  const seconds = Math.max(0, Math.floor((endTime.value - now.value) / 1000))
  return [Math.floor(seconds / 3600), Math.floor(seconds / 60) % 60, seconds % 60]
    .map((part) => String(part).padStart(2, '0')).join(':')
})
const loadData = async () => {
  if (loading.value) return
  loading.value = true
  failed.value = false
  try {
    const response = await getHomeContentAPI()
    promotion.value = response.data?.homeFlashPromotion || null
    now.value = Date.now()
  } catch {
    failed.value = true
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}
const openProduct = (id: number) => uni.navigateTo({ url: '/pages/product/product?id=' + id })
const goHome = () => uni.switchTab({ url: '/pages/index/index' })
onLoad(() => { loadData(); timer = setInterval(() => { now.value = Date.now() }, 1000) })
onUnload(() => { if (timer) clearInterval(timer) })
onPullDownRefresh(loadData)
</script>

<style lang="scss" scoped>
.flash-page { min-height: 100vh; background: #f5f5f5; }
.flash-heading { padding: 36rpx; display: flex; flex-direction: column; gap: 12rpx; color: #fff; background: linear-gradient(120deg, #ed424c, #fb7452); font-size: 26rpx; }
.title { font-size: 40rpx; font-weight: bold; }
.empty { padding: 100rpx 30rpx; display: flex; flex-direction: column; align-items: center; gap: 30rpx; color: #888; font-size: 28rpx; }
.product-list { padding: 20rpx; }
.product { display: flex; padding: 20rpx; background: #fff; border-radius: 12rpx; margin-bottom: 20rpx; }
.product image { width: 240rpx; height: 240rpx; flex-shrink: 0; }
.product-info { padding-left: 20rpx; display: flex; flex-direction: column; gap: 12rpx; min-width: 0; }
.name { font-size: 28rpx; color: #333; }
.subtitle { font-size: 22rpx; color: #888; }
.price { font-size: 36rpx; color: #e63240; }
.detail-link { color: #e63240; font-size: 24rpx; }
</style>
