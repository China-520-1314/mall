<template>
  <view class="page">
    <!-- 状态横幅 -->
    <view class="status-banner" :class="bannerClass">
      <text class="status-text">{{ statusText(detail.status) }}</text>
      <text class="status-desc">{{ statusDesc }}</text>
    </view>

    <!-- 进度时间线 -->
    <view class="card">
      <view class="card-title">售后进度</view>
      <view class="timeline">
        <view
          class="timeline-item"
          v-for="(log, idx) in reversedLogs"
          :key="log.id"
        >
          <view class="dot-col">
            <view class="dot" :class="{ active: idx === 0 }"></view>
            <view v-if="idx < reversedLogs.length - 1" class="line"></view>
          </view>
          <view class="content">
            <view class="content-top">
              <text class="log-title" :class="{ active: idx === 0 }">{{ log.title }}</text>
              <text class="log-time">{{ formatDateTime(log.createTime) }}</text>
            </view>
            <text class="log-note" v-if="log.note">{{ log.note }}</text>
            <text class="log-operator">{{ operatorTypeText(log.operatorType) }}：{{ log.operatorName }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 商家退货地址（同意后可见） -->
    <view class="card" v-if="detail.companyAddressName">
      <view class="card-title">退货寄回地址</view>
      <view class="addr-row"><text class="addr-label">收货点</text><text>{{ detail.companyAddressName }}</text></view>
      <view class="addr-row"><text class="addr-label">收货人</text><text>{{ detail.companyReceiverName }}</text></view>
      <view class="addr-row"><text class="addr-label">电话</text><text>{{ detail.companyReceiverPhone }}</text></view>
      <view class="addr-row"><text class="addr-label">地址</text><text class="addr-text">{{ detail.companyDetailAddress }}</text></view>
    </view>

    <!-- 填写寄回物流（状态=1 退货中） -->
    <view class="card" v-if="detail.status === 1">
      <view class="card-title">填写寄回物流</view>
      <view class="form-row">
        <text class="form-label">快递公司</text>
        <input v-model="deliveryForm.deliveryCompany" class="form-input" placeholder="如：顺丰、圆通" />
      </view>
      <view class="form-row">
        <text class="form-label">快递单号</text>
        <input v-model="deliveryForm.deliverySn" class="form-input" placeholder="请输入快递单号" />
      </view>
      <button class="block-primary-btn" @click="handleFillDelivery">提交物流信息</button>
    </view>

    <!-- 已寄回物流信息（状态=4/2） -->
    <view class="card" v-if="detail.status === 4 || detail.status === 2">
      <view class="card-title">我的寄回物流</view>
      <view class="addr-row"><text class="addr-label">快递公司</text><text>{{ detail.returnDeliveryCompany }}</text></view>
      <view class="addr-row"><text class="addr-label">快递单号</text><text>{{ detail.returnDeliverySn }}</text></view>
      <view class="addr-row"><text class="addr-label">寄回时间</text><text>{{ formatDateTime(detail.shipTime) }}</text></view>
    </view>

    <!-- 商品信息 -->
    <view class="card">
      <view class="card-title">售后商品</view>
      <view class="goods-box">
        <image class="pic" :src="detail.productPic" mode="aspectFill"></image>
        <view class="goods-info">
          <text class="goods-name clamp">{{ detail.productName }}</text>
          <text class="goods-spec">{{ detail.productAttr }}</text>
          <view class="goods-bottom">
            <text class="goods-price">￥{{ detail.productRealPrice }}</text>
            <text class="goods-count">x{{ detail.productCount }}</text>
          </view>
        </view>
      </view>
      <view class="info-row"><text>售后原因</text><text>{{ detail.reason }}</text></view>
      <view class="info-row" v-if="detail.description"><text>问题描述</text><text class="info-value">{{ detail.description }}</text></view>
      <view class="info-row" v-if="detail.returnAmount != null">
        <text>退款金额</text>
        <text class="amount">￥{{ detail.returnAmount }}</text>
      </view>
      <view class="info-row" v-if="detail.status === 3 && detail.handleNote">
        <text>拒绝原因</text>
        <text class="reject">{{ detail.handleNote }}</text>
      </view>
    </view>

    <!-- 底部操作 -->
    <view class="footer" v-if="detail.status === 0">
      <button class="block-ghost-btn" @click="handleCancel">撤销售后申请</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getReturnApplyDetailAPI, fillReturnDeliveryAPI, cancelReturnApplyAPI } from '@/apis/order'
import type { PortalReturnApplyDetail } from '@/types/order'

const applyId = ref(0)
const detail = ref<PortalReturnApplyDetail>({} as PortalReturnApplyDetail)

const deliveryForm = reactive({
  deliveryCompany: '',
  deliverySn: '',
})

onLoad((options) => {
  if (options?.id) {
    applyId.value = +options.id
    loadDetail()
  }
})

const loadDetail = async () => {
  try {
    const res = await getReturnApplyDetailAPI(applyId.value)
    detail.value = res.data
  } catch (e) {
    console.error('加载售后详情失败', e)
  }
}

// 时间线倒序展示（最新节点在最上面）
const reversedLogs = computed(() => [...(detail.value.logList || [])].reverse())

const statusText = (status?: number) => {
  const map: Record<number, string> = {
    0: '待商家处理',
    1: '商家已同意，待寄回',
    2: '退款已完成',
    3: '申请已拒绝',
    4: '商品已寄回，待收货',
    5: '申请已取消',
  }
  return map[status ?? -1] || '处理中'
}

const statusDesc = computed(() => {
  const map: Record<number, string> = {
    0: '商家正在审核你的售后申请，请耐心等待',
    1: '请按下方地址寄回商品并填写物流单号',
    2: '商家已确认收货，退款已原路退回',
    3: '很抱歉，你的售后申请未通过，可查看拒绝原因',
    4: '商家收到退回商品并确认后将为你退款',
    5: '你已撤销售后申请',
  }
  return map[detail.value.status] || ''
})

const bannerClass = computed(() => {
  if (detail.value.status === 2) return 'success'
  if (detail.value.status === 3 || detail.value.status === 5) return 'gray'
  return 'warning'
})

const operatorTypeText = (t: number) => {
  return ['会员', '商家', '系统'][t] || ''
}

const formatDateTime = (t?: string | null) => {
  return t ? t.replace('T', ' ').substring(0, 16) : ''
}

const handleFillDelivery = async () => {
  if (!deliveryForm.deliveryCompany.trim()) {
    uni.showToast({ icon: 'none', title: '请填写快递公司' })
    return
  }
  if (!deliveryForm.deliverySn.trim()) {
    uni.showToast({ icon: 'none', title: '请填写快递单号' })
    return
  }
  try {
    await fillReturnDeliveryAPI({
      id: applyId.value,
      deliveryCompany: deliveryForm.deliveryCompany.trim(),
      deliverySn: deliveryForm.deliverySn.trim(),
    })
    uni.showToast({ icon: 'success', title: '提交成功' })
    loadDetail()
  } catch (e) {
    console.error('填写物流失败', e)
  }
}

const handleCancel = () => {
  uni.showModal({
    title: '提示',
    content: '确定撤销售后申请吗？',
    success: async (res) => {
      if (res.confirm) {
        try {
          await cancelReturnApplyAPI(applyId.value)
          uni.showToast({ icon: 'success', title: '已撤销' })
          setTimeout(() => uni.navigateBack(), 800)
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
  padding-bottom: 140rpx;
}

.status-banner {
  padding: 40rpx 30rpx;
  display: flex;
  flex-direction: column;

  .status-text {
    font-size: 38rpx;
    font-weight: 700;
    color: #fff;
  }

  .status-desc {
    margin-top: 12rpx;
    font-size: 24rpx;
    color: rgba(255, 255, 255, 0.9);
  }

  &.warning {
    background: linear-gradient(135deg, #fa436a, #ff7a45);
  }
  &.success {
    background: linear-gradient(135deg, #34b377, #5fcda2);
  }
  &.gray {
    background: linear-gradient(135deg, #909399, #b0b3b8);
  }
}

.card {
  margin: 20rpx;
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
}

.card-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #303133;
  margin-bottom: 20rpx;
}

.timeline {
  .timeline-item {
    display: flex;
  }

  .dot-col {
    display: flex;
    flex-direction: column;
    align-items: center;
    margin-right: 20rpx;
  }

  .dot {
    width: 20rpx;
    height: 20rpx;
    border-radius: 50%;
    background: #dcdfe6;
    margin-top: 8rpx;

    &.active {
      background: #fa436a;
      box-shadow: 0 0 0 6rpx rgba(250, 67, 106, 0.15);
    }
  }

  .line {
    flex: 1;
    width: 2rpx;
    background: #ebeef5;
    margin: 6rpx 0;
    min-height: 60rpx;
  }

  .content {
    flex: 1;
    padding-bottom: 30rpx;
  }

  .content-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .log-title {
    font-size: 26rpx;
    color: #606266;

    &.active {
      color: #303133;
      font-weight: 600;
    }
  }

  .log-time {
    font-size: 22rpx;
    color: #c0c4cc;
  }

  .log-note {
    display: block;
    margin-top: 8rpx;
    font-size: 24rpx;
    color: #909399;
    line-height: 1.5;
  }

  .log-operator {
    display: block;
    margin-top: 6rpx;
    font-size: 22rpx;
    color: #c0c4cc;
  }
}

.addr-row,
.info-row {
  display: flex;
  font-size: 26rpx;
  color: #303133;
  padding: 14rpx 0;

  .addr-label {
    width: 160rpx;
    color: #909399;
  }
}

.addr-text {
  flex: 1;
}

.form-row {
  display: flex;
  align-items: center;
  border-bottom: 1rpx solid #f0f0f0;
  padding: 8rpx 0;

  .form-label {
    width: 160rpx;
    font-size: 26rpx;
    color: #303133;
  }

  .form-input {
    flex: 1;
    font-size: 26rpx;
    height: 72rpx;
  }
}

.block-primary-btn {
  margin-top: 24rpx;
  background: #fa436a;
  color: #fff;
  border-radius: 44rpx;
  font-size: 28rpx;
}

.goods-box {
  display: flex;
  padding-bottom: 20rpx;
  border-bottom: 1rpx solid #f0f0f0;

  .pic {
    width: 120rpx;
    height: 120rpx;
    border-radius: 8rpx;
    background: #f5f5f5;
  }

  .goods-info {
    flex: 1;
    margin-left: 20rpx;
    display: flex;
    flex-direction: column;
  }

  .goods-name {
    font-size: 26rpx;
    color: #303133;
  }

  .goods-spec {
    font-size: 22rpx;
    color: #909399;
    margin-top: 8rpx;
  }

  .goods-bottom {
    display: flex;
    justify-content: space-between;
    margin-top: 10rpx;
  }

  .goods-price {
    color: #fa436a;
    font-size: 28rpx;
    font-weight: 600;
  }

  .goods-count {
    font-size: 24rpx;
    color: #909399;
  }
}

.info-row {
  justify-content: space-between;
  border-bottom: 1rpx solid #f7f8fa;

  .info-value {
    max-width: 460rpx;
    text-align: right;
    color: #606266;
  }

  .amount {
    color: #fa436a;
    font-weight: 600;
  }

  .reject {
    color: #f56c6c;
    max-width: 460rpx;
    text-align: right;
  }
}

.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 30rpx;
  background: #fff;
}

.block-ghost-btn {
  background: #fff;
  color: #f56c6c;
  border: 1rpx solid #f56c6c;
  border-radius: 44rpx;
  font-size: 28rpx;
}
</style>
