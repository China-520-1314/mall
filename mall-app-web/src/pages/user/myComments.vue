<template>
  <view class="page">
    <view class="page-heading">
      <text class="heading">我的评价</text>
      <text class="description">查看购买评价，以及其他人对你的评价的回复</text>
    </view>

    <view class="tabs">
      <button :class="{ active: activeTab === 'comments' }" @click="changeTab('comments')">我的评价</button>
      <button :class="{ active: activeTab === 'replies' }" @click="changeTab('replies')">收到的回复</button>
    </view>

    <view v-if="loading && visibleCount === 0" class="empty">正在加载...</view>
    <view v-else-if="loadFailed && visibleCount === 0" class="empty">
      <text>加载失败，请稍后重试</text>
      <button class="primary-btn" @click="loadData()">重新加载</button>
    </view>
    <view v-else-if="visibleCount === 0" class="empty">
      <text>{{ activeTab === 'comments' ? '你还没有发布商品评价' : '暂时没有收到其他人的回复' }}</text>
      <text class="empty-tip">{{ activeTab === 'comments' ? '确认收货后，可在已完成订单中评价购买的商品。' : '其他人回复你的商品评价后，会在这里显示。' }}</text>
      <button v-if="activeTab === 'comments'" class="primary-btn" @click="openCompletedOrders">查看已完成订单</button>
    </view>

    <template v-if="activeTab === 'comments'">
      <view v-for="item in comments" :key="item.id" class="comment-card">
        <view class="card-head" @click="openProduct(item.productId)">
          <image v-if="item.productPic" class="product-pic" :src="resolveProductMediaUrl(item.productPic)" mode="aspectFill" />
          <text class="product-name">{{ item.productName }}</text>
          <text class="link">查看商品 ›</text>
        </view>
        <view class="meta-row">
          <text class="stars">{{ '★'.repeat(item.star || 0) }}{{ '☆'.repeat(5 - (item.star || 0)) }}</text>
          <text class="time">{{ formatTime(item.createTime) }}</text>
        </view>
        <text class="comment-content">{{ item.content }}</text>
        <view v-if="splitPics(item.pics).length" class="image-list">
          <image v-for="pic in splitPics(item.pics)" :key="pic" :src="resolveProductMediaUrl(pic)" mode="aspectFill" @click="previewPics(item.pics, pic)" />
        </view>
        <view class="card-footer">
          <text class="counts">{{ item.collectCouont || 0 }} 人点赞 · {{ item.replayCount || 0 }} 条回复</text>
          <view class="actions">
            <button class="text-btn" @click="openProduct(item.productId)">查看讨论</button>
            <button class="text-btn danger" :disabled="deletingId === item.id" @click="removeComment(item)">删除</button>
          </view>
        </view>
      </view>
    </template>

    <template v-else>
      <view v-for="item in receivedReplies" :key="item.replyId" class="comment-card">
        <view class="reply-author">
          <image :src="item.replyMemberIcon || '/static/missing-face.png'" mode="aspectFill" />
          <view class="author-info">
            <text class="author-name">{{ item.replyMemberNickName || '商城用户' }} <text class="reply-label">回复了你的评价</text></text>
            <text class="time">{{ formatTime(item.replyCreateTime) }}</text>
          </view>
        </view>
        <text class="comment-content">{{ item.replyContent }}</text>
        <view class="quoted-comment">
          <text class="quote-title">我的评价 · {{ item.productName }}</text>
          <text class="quote-content">{{ item.commentContent }}</text>
        </view>
        <view class="card-footer">
          <text class="counts">商品评价讨论</text>
          <button class="text-btn" @click="openProduct(item.productId)">查看讨论 ›</button>
        </view>
      </view>
    </template>

    <button v-if="visibleCount > 0 && hasMore" class="load-more" :disabled="loading" @click="loadData(false)">{{ loading ? '正在加载...' : '加载更多' }}</button>
    <view v-else-if="visibleCount > 0" class="end-tip">已显示全部{{ activeTab === 'comments' ? '评价' : '回复' }}</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { useMemberStore } from '@/stores/member'
import { formatDate } from '@/utils/date'
import {
  getMyProductCommentsAPI,
  getReceivedCommentRepliesAPI,
  deleteCommentAPI,
  resolveProductMediaUrl,
} from '@/apis/product'
import type { MyProductComment, ReceivedCommentReply } from '@/types/product'

type Tab = 'comments' | 'replies'
const memberStore = useMemberStore()
const activeTab = ref<Tab>('comments')
const comments = ref<MyProductComment[]>([])
const receivedReplies = ref<ReceivedCommentReply[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const hasMore = ref(false)
const currentPage = ref(0)
const deletingId = ref<number | null>(null)
let requestVersion = 0
const visibleCount = computed(() => activeTab.value === 'comments' ? comments.value.length : receivedReplies.value.length)

const loadData = async (reset = true) => {
  if (!memberStore.hasLogin) return
  if (!reset && (loading.value || !hasMore.value)) return
  const version = ++requestVersion
  const tab = activeTab.value
  const pageNum = reset ? 1 : currentPage.value + 1
  loading.value = true
  loadFailed.value = false
  try {
    if (tab === 'comments') {
      const result = await getMyProductCommentsAPI(pageNum)
      if (version !== requestVersion) return
      comments.value = reset ? result.data.list : [...comments.value, ...result.data.list]
      hasMore.value = pageNum < result.data.totalPage
    } else {
      const result = await getReceivedCommentRepliesAPI(pageNum)
      if (version !== requestVersion) return
      receivedReplies.value = reset ? result.data.list : [...receivedReplies.value, ...result.data.list]
      hasMore.value = pageNum < result.data.totalPage
    }
    currentPage.value = pageNum
  } catch (error) {
    if (version !== requestVersion) return
    loadFailed.value = true
    console.error('加载我的评价失败:', error)
  } finally {
    if (version === requestVersion) {
      loading.value = false
      uni.stopPullDownRefresh()
    }
  }
}

const changeTab = (tab: Tab) => {
  if (tab === activeTab.value) return
  activeTab.value = tab
  hasMore.value = false
  currentPage.value = 0
  loadData()
}

const openProduct = (productId: number) => uni.navigateTo({ url: `/pages/product/product?id=${productId}` })
const openCompletedOrders = () => uni.navigateTo({ url: '/pages/order/order?state=3' })
const formatTime = (value: string) => value ? formatDate(new Date(value), 'yyyy-MM-dd hh:mm') : ''
const splitPics = (pics?: string) => pics ? pics.split(',').map((pic) => pic.trim()).filter(Boolean) : []
const previewPics = (pics: string | undefined, current: string) => uni.previewImage({
  current: resolveProductMediaUrl(current),
  urls: splitPics(pics).map(resolveProductMediaUrl),
})

const removeComment = async (comment: MyProductComment) => {
  if (deletingId.value !== null) return
  const confirmed = await new Promise<boolean>((resolve) => uni.showModal({
    title: '删除评价',
    content: '删除后，这条评价和相关回复将不再展示，该购买不能重新评价。确定删除？',
    confirmText: '删除',
    success: (result) => resolve(!!result.confirm),
    fail: () => resolve(false),
  }))
  if (!confirmed) return
  deletingId.value = comment.id
  try {
    await deleteCommentAPI(comment.id)
    comments.value = comments.value.filter((item) => item.id !== comment.id)
    receivedReplies.value = receivedReplies.value.filter((item) => item.commentId !== comment.id)
    await loadData()
    uni.showToast({ title: '评价已删除', icon: 'success' })
  } catch (error) {
    console.error('删除评价失败:', error)
  } finally {
    deletingId.value = null
  }
}

onShow(() => {
  if (!memberStore.hasLogin) {
    uni.redirectTo({ url: '/pages/public/login' })
    return
  }
  loadData()
})
onPullDownRefresh(() => loadData())
onReachBottom(() => loadData(false))
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; max-width: 1000px; margin: 0 auto; padding: 28rpx 30rpx 60rpx; background: #f7f7f7; }
.page-heading { padding: 12rpx 4rpx 28rpx; }
.heading { display: block; color: #303133; font-size: 38rpx; font-weight: 600; }
.description { display: block; margin-top: 12rpx; color: #909399; font-size: 26rpx; }
.tabs { display: flex; gap: 18rpx; margin-bottom: 26rpx; border-bottom: 1rpx solid #e7e7e7; }
.tabs button { margin: 0; padding: 12rpx 24rpx 20rpx; border-radius: 0; background: transparent; font-size: 28rpx; color: #606266; line-height: 1.5; }
.tabs button::after, .text-btn::after, .load-more::after { border: 0; }
.tabs button.active { color: #fa436a; border-bottom: 4rpx solid #fa436a; font-weight: 600; }
.comment-card { margin-bottom: 22rpx; padding: 28rpx; border-radius: 16rpx; background: #fff; }
.card-head { display: flex; justify-content: space-between; gap: 24rpx; align-items: center; cursor: pointer; }
.product-pic { flex-shrink: 0; width: 90rpx; height: 90rpx; border-radius: 8rpx; }
.product-name { flex: 1; font-size: 30rpx; font-weight: 600; color: #303133; }
.link { flex-shrink: 0; font-size: 24rpx; color: #909399; }
.meta-row { display: flex; align-items: center; gap: 24rpx; margin-top: 18rpx; }
.stars { color: #ffad23; letter-spacing: 3rpx; font-size: 30rpx; }
.time { color: #999; font-size: 22rpx; }
.comment-content { display: block; margin-top: 22rpx; font-size: 28rpx; color: #45474c; line-height: 1.75; white-space: pre-wrap; word-break: break-word; }
.image-list { display: flex; flex-wrap: wrap; gap: 14rpx; margin-top: 20rpx; }
.image-list image { width: 150rpx; height: 150rpx; border-radius: 10rpx; cursor: pointer; }
.card-footer { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; margin-top: 24rpx; padding-top: 18rpx; border-top: 1rpx solid #f1f1f1; }
.counts { font-size: 23rpx; color: #909399; }
.actions { display: flex; gap: 20rpx; }
.text-btn { margin: 0; padding: 0; background: transparent; color: #fa436a; font-size: 24rpx; line-height: 1.8; }
.text-btn.danger { color: #909399; }
.reply-author { display: flex; align-items: center; gap: 18rpx; }
.reply-author image { width: 70rpx; height: 70rpx; border-radius: 50%; }
.author-info { display: flex; flex-direction: column; gap: 8rpx; }
.author-name { color: #303133; font-size: 27rpx; }
.reply-label { color: #909399; font-size: 24rpx; }
.quoted-comment { margin-top: 22rpx; padding: 20rpx; border-radius: 10rpx; background: #f8f8fa; border-left: 4rpx solid #e7e7ec; }
.quote-title { display: block; color: #606266; font-size: 24rpx; }
.quote-content { display: block; margin-top: 10rpx; color: #909399; font-size: 25rpx; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
.empty { padding: 110rpx 24rpx; text-align: center; color: #606266; font-size: 28rpx; }
.empty-tip { display: block; margin-top: 20rpx; color: #999; font-size: 24rpx; line-height: 1.7; }
.primary-btn { display: inline-block; margin-top: 32rpx; padding: 0 34rpx; border-radius: 40rpx; background: #fa436a; color: #fff; font-size: 26rpx; }
.load-more, .end-tip { margin: 20rpx auto 0; padding: 20rpx; text-align: center; font-size: 24rpx; color: #999; background: transparent; }
</style>
