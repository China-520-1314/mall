<template>
  <view class="app">
    <view class="price-box">
      <text>支付金额</text>
      <text class="price">{{ orderInfo.payAmount }}</text>
      <text class="deadline">剩余支付时间 {{ countdownText }}</text>
    </view>

    <view class="pay-type-list">
      <view class="type-item b-b" :class="{ disabled: paying }" @click="handlePay(1)">
        <text class="icon yticon icon-alipay"></text>
        <view class="con">
          <text class="tit">支付宝支付</text>
        </view>
        <text class="pay-action">{{ paying && payType === 1 ? '支付中...' : '点击支付' }}</text>
      </view>
      <view class="type-item b-b" :class="{ disabled: paying }" @click="handlePay(2)">
        <text class="icon yticon icon-weixinzhifu"></text>
        <view class="con">
          <text class="tit">微信支付</text>
        </view>
        <text class="pay-action">{{ paying && payType === 2 ? '支付中...' : '点击支付' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { cancelUserOrderAPI, getOrderDetailAPI, payOrderSuccessAPI } from '@/apis/order'
import type { OmsOrderDetail } from '@/types/order'
import { formatPaymentCountdown, getPaymentRemainingSeconds } from '@/utils/orderPayment'

// ===== 页面数据 =====
// 订单ID
const orderId = ref<number | null>(null)
// 支付方式：1->支付宝；2->微信
const payType = ref(1)
const paying = ref(false)
// 订单信息
const orderInfo = ref<Partial<OmsOrderDetail>>({})
const countdownText = ref('--:--')
let countdownTimer: ReturnType<typeof setInterval> | null = null
let expiredHandled = false

const redirectToPendingOrders = () => {
  uni.redirectTo({ url: '/pages/order/order?state=1' })
}

const handleExpiredOrder = async () => {
  if (expiredHandled || orderInfo.value.status !== 0 || orderId.value == null) return
  expiredHandled = true
  if (countdownTimer) clearInterval(countdownTimer)
  try {
    await cancelUserOrderAPI(orderId.value)
  } catch (e) {
    console.warn('订单可能已由系统自动取消', e)
  }
  uni.showToast({ title: '订单已超时，已自动取消', icon: 'none' })
  setTimeout(redirectToPendingOrders, 800)
}

const updateCountdown = () => {
  countdownText.value = formatPaymentCountdown(orderInfo.value.paymentExpireTime)
  if (getPaymentRemainingSeconds(orderInfo.value.paymentExpireTime) === 0) {
    void handleExpiredOrder()
  }
}

// ===== 数据加载 =====
// 加载订单详情
const loadData = async () => {
  try {
    const res = await getOrderDetailAPI(orderId.value!)
    orderInfo.value = res.data
    if (orderInfo.value.status !== 0) {
      uni.showToast({ title: '该订单已无法支付', icon: 'none' })
      setTimeout(redirectToPendingOrders, 800)
      return
    }
    updateCountdown()
    countdownTimer = setInterval(updateCountdown, 1000)
  } catch (e) {
    console.error('加载订单详情失败', e)
  }
}

// ===== 生命周期 =====
// 页面加载
onLoad(async (options: any) => {
  orderId.value = Number(options.orderId)
  await loadData()
})

onUnload(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

// ===== 事件处理方法 =====
// 选择支付方式后立即执行本地模拟支付
const handlePay = async (type: number) => {
  if (paying.value) return
  payType.value = type
  if (getPaymentRemainingSeconds(orderInfo.value.paymentExpireTime) === 0) {
    await handleExpiredOrder()
    return
  }
  paying.value = true
  try {
    await payOrderSuccessAPI({
      orderId: orderId.value!,
      payType: payType.value,
    })
    uni.redirectTo({
      url: '/pages/money/paySuccess',
    })
  } catch (e) {
    paying.value = false
    console.error('支付失败', e)
  }
}
</script>

<style lang="scss" scoped>
.app {
  width: 100%;
}

.price-box {
  background-color: #fff;
  height: 265rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  font-size: 28rpx;
  color: #909399;

  .price {
    font-size: 50rpx;
    color: #303133;
    margin-top: 12rpx;

    &:before {
      content: '￥';
      font-size: 40rpx;
    }
  }

  .deadline {
    margin-top: 18rpx;
    color: $base-color;
  }
}

.pay-type-list {
  margin-top: 20rpx;
  background-color: #fff;
  padding-left: 60rpx;

  .type-item {
    height: 120rpx;
    padding: 20rpx 0;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-right: 60rpx;
    font-size: 30rpx;
    position: relative;

    &.disabled {
      opacity: 0.6;
    }
  }

  .icon {
    width: 100rpx;
    font-size: 52rpx;
  }

  .icon-erjiye-yucunkuan {
    color: #fe8e2e;
  }

  .icon-weixinzhifu {
    color: #36cb59;
  }

  .icon-alipay {
    color: #01aaef;
  }

  .tit {
    font-size: $font-lg;
    color: $font-color-dark;
    margin-bottom: 4rpx;
  }

  .con {
    flex: 1;
    display: flex;
    flex-direction: column;
    font-size: $font-sm;
    color: $font-color-light;
  }

  .pay-action {
    color: $base-color;
    font-size: 26rpx;
  }
}
</style>
