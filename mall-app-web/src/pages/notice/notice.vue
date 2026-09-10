<template>
  <view class="page">
    <view v-if="loading" class="empty">正在加载消息...</view>
    <view v-else-if="messages.length === 0" class="empty">暂无站内信</view>
    <view v-for="item in messages" :key="item.id" class="message-card" :class="{ unread: item.readStatus === 0 }" @click="openMessage(item)">
      <view class="message-head">
        <text class="title">{{ item.title }}</text>
        <text v-if="item.readStatus === 0" class="badge">未读</text>
      </view>
      <text class="time">{{ formatTime(item.createTime) }}</text>
      <text class="content">{{ item.content }}</text>
      <text v-if="item.orderId" class="order">关联订单：{{ item.orderId }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { getMemberMessageListAPI, markMemberMessageReadAPI, type MemberMessage } from '@/apis/memberMessage'
import { formatDate } from '@/utils/date'

const messages = ref<MemberMessage[]>([])
const loading = ref(false)

const formatTime = (value: string) => {
  if (!value) return ''
  return formatDate(new Date(value), 'yyyy-MM-dd hh:mm')
}

const loadMessages = async () => {
  loading.value = true
  try {
    const res = await getMemberMessageListAPI({ pageNum: 1, pageSize: 50 })
    messages.value = res.data?.list || []
  } finally {
    loading.value = false
  }
}

const openMessage = async (message: MemberMessage) => {
  if (message.readStatus === 0) {
    await markMemberMessageReadAPI(message.id)
    message.readStatus = 1
  }
}

onShow(loadMessages)
</script>

<style lang="scss" scoped>
page { background: #f7f7f7; }
.page { min-height: 100vh; padding: 24rpx; }
.empty { padding: 160rpx 0; color: #909399; text-align: center; font-size: 28rpx; }
.message-card { margin-bottom: 20rpx; padding: 24rpx; border-radius: 14rpx; background: #fff; box-shadow: 0 4rpx 18rpx rgba(30, 36, 50, .04); }
.message-card.unread { border-left: 6rpx solid #fa436a; }
.message-head { display: flex; align-items: center; justify-content: space-between; }
.title { color: #303133; font-size: 30rpx; font-weight: 600; }
.badge { padding: 4rpx 10rpx; border-radius: 16rpx; background: #fff0f3; color: #fa436a; font-size: 20rpx; }
.time, .order { display: block; margin-top: 10rpx; color: #909399; font-size: 22rpx; }
.content { display: block; margin-top: 18rpx; color: #606266; font-size: 26rpx; line-height: 1.6; }
</style>
