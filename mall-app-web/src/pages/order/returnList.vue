<template>
  <view class="page">
    <view v-if="list.length === 0 && !loading" class="empty">
      <text class="empty-icon">📦</text>
      <text class="empty-text">暂无售后记录</text>
    </view>

    <view
      class="apply-card"
      v-for="item in list"
      :key="item.id"
      @click="handleViewDetail(item.id)"
    >
      <view class="card-header">
        <text class="order-sn">订单号：{{ item.orderSn }}</text>
        <text class="status" :class="statusClass(item.status)">{{ statusText(item.status) }}</text>
      </view>
      <view class="card-body">
        <image class="pic" :src="item.productPic" mode="aspectFill"></image>
        <view class="info">
          <text class="name clamp">{{ item.productName }}</text>
          <text class="reason">售后原因：{{ item.reason }}</text>
          <view class="bottom">
            <text class="amount" v-if="item.returnAmount != null">退款金额：￥{{ item.returnAmount }}</text>
            <text class="time">{{ formatDateTime(item.createTime) }}</text>
          </view>
        </view>
      </view>
      <view class="card-footer">
        <button
          v-if="item.status === 0"
          class="ghost-btn"
          @click.stop="handleCancel(item.id)"
        >
          撤销申请
        </button>
        <button
          v-if="item.status === 1"
          class="primary-btn"
          @click.stop="handleViewDetail(item.id)"
        >
          填写寄回物流
        </button>
        <button class="ghost-btn" @click.stop="handleViewDetail(item.id)">查看进度</button>
      </view>
    </view>

    <view v-if="list.length > 0" class="load-more">
      <text v-if="loading">加载中...</text>
      <text v-else-if="!hasMore">没有更多了</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow, onReachBottom } from '@dcloudio/uni-app'
import { getReturnApplyListAPI, cancelReturnApplyAPI } from '@/apis/order'
import type { OmsOrderReturnApply } from '@/types/order'

const list = ref<OmsOrderReturnApply[]>([])
const pageNum = ref(1)
const pageSize = 5
const total = ref(0)
const loading = ref(false)

const hasMore = () => list.value.length < total.value

const statusText = (status: number) => {
  const map: Record<number, string> = {
    0: '待商家处理',
    1: '待寄回商品',
    2: '已完成',
    3: '已拒绝',
    4: '待商家收货',
    5: '已取消',
  }
  return map[status] || '未知状态'
}

const statusClass = (status: number) => {
  if (status === 2) return 'success'
  if (status === 3 || status === 5) return 'gray'
  return 'warning'
}

const formatDateTime = (t: string) => {
  return t ? t.replace('T', ' ').substring(0, 16) : ''
}

const loadList = async (reset = false) => {
  if (loading.value) return
  if (reset) {
    pageNum.value = 1
    list.value = []
  }
  loading.value = true
  try {
    const res = await getReturnApplyListAPI({ pageNum: pageNum.value, pageSize })
    total.value = res.data.total
    list.value = [...list.value, ...res.data.list]
  } catch (e) {
    console.error('加载售后列表失败', e)
  } finally {
    loading.value = false
  }
}

onShow(() => {
  loadList(true)
})

onReachBottom(() => {
  if (hasMore() && !loading.value) {
    pageNum.value++
    loadList()
  }
})

const handleViewDetail = (id: number) => {
  uni.navigateTo({ url: `/pages/order/returnDetail?id=${id}` })
}

const handleCancel = (id: number) => {
  uni.showModal({
    title: '提示',
    content: '确定撤销售后申请吗？',
    success: async (res) => {
      if (res.confirm) {
        try {
          await cancelReturnApplyAPI(id)
          uni.showToast({ icon: 'success', title: '已撤销' })
          loadList(true)
        } catch (e) {
          console.error('撤销失败', e)
        }
      }
    },
  })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f5f5f5;
  padding: 20rpx;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 200rpx;

  .empty-icon {
    font-size: 100rpx;
  }

  .empty-text {
    margin-top: 20rpx;
    color: #909399;
    font-size: 26rpx;
  }
}

.apply-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: 20rpx;
    border-bottom: 1rpx solid #f0f0f0;
  }

  .order-sn {
    font-size: 24rpx;
    color: #606266;
  }

  .status {
    font-size: 26rpx;
    font-weight: 600;

    &.warning {
      color: #fa436a;
    }
    &.success {
      color: #5fcda2;
    }
    &.gray {
      color: #909399;
    }
  }

  .card-body {
    display: flex;
    padding: 20rpx 0;
  }

  .pic {
    width: 140rpx;
    height: 140rpx;
    border-radius: 8rpx;
    background: #f5f5f5;
  }

  .info {
    flex: 1;
    margin-left: 20rpx;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
  }

  .name {
    font-size: 26rpx;
    color: #303133;
  }

  .reason {
    font-size: 22rpx;
    color: #909399;
  }

  .bottom {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .amount {
    font-size: 24rpx;
    color: #fa436a;
  }

  .time {
    font-size: 22rpx;
    color: #c0c4cc;
  }

  .card-footer {
    display: flex;
    justify-content: flex-end;
    gap: 20rpx;
    border-top: 1rpx solid #f0f0f0;
    padding-top: 20rpx;
  }

  .primary-btn,
  .ghost-btn {
    font-size: 24rpx;
    padding: 0 28rpx;
    height: 56rpx;
    line-height: 56rpx;
    border-radius: 28rpx;
    margin: 0;
  }

  .primary-btn {
    background: #fa436a;
    color: #fff;
  }

  .ghost-btn {
    background: #fff;
    color: #606266;
    border: 1rpx solid #dcdfe6;
  }
}

.load-more {
  text-align: center;
  padding: 30rpx;
  color: #c0c4cc;
  font-size: 24rpx;
}
</style>
