<template>
  <view class="page">
    <!-- 选择退货商品 -->
    <view class="card">
      <view class="card-title">选择售后商品</view>
      <view
        class="goods-item"
        v-for="item in order.orderItemList"
        :key="item.id"
        @click="handleSelectItem(item)"
      >
        <view class="radio" :class="{ active: selectedItemId === item.id }">
          <text v-if="selectedItemId === item.id" class="check-mark">✓</text>
        </view>
        <image class="pic" :src="item.productPic" mode="aspectFill"></image>
        <view class="info">
          <text class="name clamp">{{ item.productName }}</text>
          <text class="spec">{{ formatAttr(item.productAttr) }}</text>
          <view class="bottom">
            <text class="price">￥{{ item.productPrice }}</text>
            <text class="count">x{{ item.productQuantity }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 售后信息 -->
    <view class="card">
      <view class="form-row">
        <text class="label">退货数量</text>
        <view class="stepper">
          <view class="step-btn" @click="changeCount(-1)">-</view>
          <text class="step-num">{{ applyForm.productCount }}</text>
          <view class="step-btn" @click="changeCount(1)">+</view>
        </view>
      </view>
      <view class="form-row" @click="openReasonPicker">
        <text class="label">售后原因</text>
        <view class="value-box">
          <text class="value" :class="{ placeholder: !applyForm.reason }">
            {{ applyForm.reason || '请选择原因' }}
          </text>
          <text class="arrow">›</text>
        </view>
      </view>
      <view class="form-column">
        <text class="label">问题描述</text>
        <textarea
          v-model="applyForm.description"
          class="textarea"
          placeholder="请描述商品存在的问题，以便商家更快处理"
          maxlength="300"
        />
      </view>
      <view class="form-row">
        <text class="label">联系人</text>
        <input v-model="applyForm.returnName" class="input" placeholder="请输入联系人姓名" />
      </view>
      <view class="form-row">
        <text class="label">联系电话</text>
        <input v-model="applyForm.returnPhone" class="input" placeholder="请输入手机号码" type="number" />
      </view>
    </view>

    <view class="tips">提示：提交后商家会尽快审核，审核通过后请按商家提供的地址寄回商品并填写快递单号。</view>

    <view class="footer">
      <button class="submit-btn" @click="handleSubmit">提交申请</button>
    </view>

    <!-- 原因选择 -->
    <uni-popup ref="reasonPopupRef" type="bottom" is-mask-click>
      <view class="picker-wrap">
        <view class="picker-header">
          <text @click="closeReasonPicker">取消</text>
          <text class="picker-title">选择售后原因</text>
          <text></text>
        </view>
        <view
          class="reason-item"
          v-for="r in reasonOptions"
          :key="r"
          @click="handleSelectReason(r)"
        >
          {{ r }}
        </view>
      </view>
    </uni-popup>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getOrderDetailAPI, createReturnApplyAPI } from '@/apis/order'
import { useMemberStore } from '@/stores/member'
import type { OmsOrderDetail, OmsOrderItem, OmsOrderReturnApplyParam } from '@/types/order'

const memberStore = useMemberStore()

const orderId = ref(0)
const order = ref<OmsOrderDetail>({} as OmsOrderDetail)
const selectedItemId = ref<number>(0)
const reasonPopupRef = ref()

const openReasonPicker = () => {
  reasonPopupRef.value?.open('bottom')
}

const closeReasonPicker = () => {
  reasonPopupRef.value?.close()
}

const reasonOptions = [
  '质量问题',
  '商品与描述不符',
  '收到商品损坏',
  '发错货/漏发',
  '不想要了',
  '七天无理由退货',
  '其他原因',
]

const applyForm = reactive({
  productCount: 1,
  reason: '',
  description: '',
  returnName: memberStore.memberInfo?.nickname || memberStore.memberInfo?.username || '',
  returnPhone: '',
})

let currentItem: OmsOrderItem | undefined

onLoad((options) => {
  if (options?.orderId) {
    orderId.value = +options.orderId
    loadOrder()
  }
})

const loadOrder = async () => {
  try {
    const res = await getOrderDetailAPI(orderId.value)
    order.value = res.data
    const first = order.value.orderItemList?.[0]
    if (first) {
      selectedItemId.value = first.id
      applyForm.productCount = first.productQuantity
    }
  } catch (e) {
    console.error('加载订单失败', e)
  }
}

const formatAttr = (attr: string) => {
  if (!attr) return ''
  try {
    const obj = JSON.parse(attr)
    return Object.values(obj).join(' ')
  } catch {
    return attr
  }
}

const changeCount = (delta: number) => {
  const item = order.value.orderItemList?.find((i) => i.id === selectedItemId.value)
  const max = item?.productQuantity || 1
  const next = applyForm.productCount + delta
  if (next < 1) return
  if (next > max) {
    uni.showToast({ icon: 'none', title: '不能超过购买数量' })
    return
  }
  applyForm.productCount = next
}

const handleSelectReason = (r: string) => {
  applyForm.reason = r
  closeReasonPicker()
}

// 选择商品，数量重置为该商品购买量
const handleSelectItem = (item: OmsOrderItem) => {
  selectedItemId.value = item.id
  applyForm.productCount = item.productQuantity
}

const handleSubmit = async () => {
  currentItem = order.value.orderItemList?.find((i) => i.id === selectedItemId.value)
  if (!currentItem) {
    uni.showToast({ icon: 'none', title: '请选择售后商品' })
    return
  }
  if (!applyForm.reason) {
    uni.showToast({ icon: 'none', title: '请选择售后原因' })
    return
  }
  if (!applyForm.returnName.trim()) {
    uni.showToast({ icon: 'none', title: '请填写联系人' })
    return
  }
  if (!/^1\d{10}$/.test(applyForm.returnPhone)) {
    uni.showToast({ icon: 'none', title: '请填写正确的手机号' })
    return
  }

  const params: OmsOrderReturnApplyParam = {
    orderId: order.value.id,
    orderSn: order.value.orderSn,
    memberUsername: memberStore.memberInfo?.username || '',
    productId: currentItem.productId,
    productName: currentItem.productName,
    productPic: currentItem.productPic,
    productAttr: currentItem.productAttr,
    productBrand: currentItem.productBrand,
    productPrice: currentItem.productPrice,
    productRealPrice: currentItem.realAmount / currentItem.productQuantity,
    productCount: applyForm.productCount,
    reason: applyForm.reason,
    description: applyForm.description,
    proofPics: '',
    returnName: applyForm.returnName,
    returnPhone: applyForm.returnPhone,
  }

  try {
    await createReturnApplyAPI(params)
    uni.showToast({ icon: 'success', title: '提交成功' })
    setTimeout(() => {
      uni.redirectTo({ url: '/pages/order/returnList' })
    }, 800)
  } catch (e) {
    console.error('提交售后申请失败', e)
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 140rpx;
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

.goods-item {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f0f0f0;

  .radio {
    width: 36rpx;
    height: 36rpx;
    border-radius: 50%;
    border: 2rpx solid #c0c4cc;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-right: 16rpx;

    .check-mark {
      font-size: 24rpx;
      color: #fff;
      font-weight: bold;
    }

    &.active {
      background: #fa436a;
      border-color: #fa436a;
    }
  }

  .pic {
    width: 120rpx;
    height: 120rpx;
    border-radius: 8rpx;
    background: #f5f5f5;
  }

  .info {
    flex: 1;
    margin-left: 20rpx;
    display: flex;
    flex-direction: column;
  }

  .name {
    font-size: 26rpx;
    color: #303133;
    line-height: 1.4;
  }

  .spec {
    font-size: 22rpx;
    color: #909399;
    margin-top: 8rpx;
  }

  .bottom {
    display: flex;
    justify-content: space-between;
    margin-top: 10rpx;
  }

  .price {
    font-size: 28rpx;
    color: #fa436a;
    font-weight: 600;
  }

  .count {
    font-size: 24rpx;
    color: #909399;
  }
}

.form-row {
  display: flex;
  align-items: center;
  min-height: 88rpx;
  border-bottom: 1rpx solid #f0f0f0;

  .label {
    width: 160rpx;
    font-size: 26rpx;
    color: #303133;
  }

  .input {
    flex: 1;
    font-size: 26rpx;
  }

  .value-box {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .placeholder {
    color: #c0c4cc;
  }

  .arrow {
    color: #c0c4cc;
    font-size: 24rpx;
  }
}

.stepper {
  display: flex;
  align-items: center;

  .step-btn {
    width: 56rpx;
    height: 48rpx;
    line-height: 44rpx;
    text-align: center;
    border: 1rpx solid #dcdfe6;
    font-size: 32rpx;
    color: #606266;
  }

  .step-num {
    width: 80rpx;
    height: 48rpx;
    line-height: 48rpx;
    text-align: center;
    border-top: 1rpx solid #dcdfe6;
    border-bottom: 1rpx solid #dcdfe6;
    font-size: 26rpx;
  }
}

.form-column {
  padding: 20rpx 0;

  .label {
    font-size: 26rpx;
    color: #303133;
    display: block;
    margin-bottom: 16rpx;
  }

  .textarea {
    width: 100%;
    height: 160rpx;
    background: #f7f8fa;
    border-radius: 8rpx;
    padding: 16rpx;
    font-size: 26rpx;
    box-sizing: border-box;
  }
}

.tips {
  margin: 20rpx;
  font-size: 22rpx;
  color: #909399;
  line-height: 1.6;
}

.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 30rpx;
  background: #fff;

  .submit-btn {
    background: #fa436a;
    color: #fff;
    border-radius: 44rpx;
    font-size: 30rpx;
  }
}

.picker-wrap {
  background: #fff;
  border-radius: 24rpx 24rpx 0 0;
  padding-bottom: 30rpx;

  .picker-header {
    display: flex;
    justify-content: space-between;
    padding: 24rpx 30rpx;
    font-size: 26rpx;
    color: #909399;
    border-bottom: 1rpx solid #f0f0f0;
  }

  .picker-title {
    color: #303133;
    font-weight: 600;
  }

  .reason-item {
    padding: 28rpx 30rpx;
    font-size: 28rpx;
    color: #303133;
    border-bottom: 1rpx solid #f7f8fa;
  }
}
</style>
