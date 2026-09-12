<template>
  <view class="page">
    <view v-if="items.length === 0" class="empty">没有可评价的商品</view>
    <view v-for="(item, index) in items" :key="item.id" class="comment-card">
      <view class="product-row">
        <image :src="item.productPic" mode="aspectFill" />
        <view class="product-info">
          <text class="product-name">{{ item.productName }}</text>
          <text class="product-attr">{{ formatProductAttr(item.productAttr) }}</text>
        </view>
      </view>
      <view class="star-row">
        <text>评分</text>
        <text v-for="star in 5" :key="star" class="star" :class="{ active: forms[index].star >= star }" @click="forms[index].star = star">★</text>
      </view>
      <textarea v-model="forms[index].content" maxlength="500" placeholder="说说商品的使用感受吧（最多500字）" />
      <view class="image-list">
        <view v-for="(pic, picIndex) in forms[index].pics" :key="pic" class="image-item">
          <image :src="resolveProductMediaUrl(pic)" mode="aspectFill" @click="previewImage(index, picIndex)" />
          <button class="remove-image" aria-label="删除图片" @click="removeImage(index, picIndex)">×</button>
        </view>
        <button
          v-if="forms[index].pics.length < 5"
          class="add-image"
          :disabled="forms[index].uploading"
          @click="chooseImages(index)"
        >
          <text class="plus">+</text>
          <text>{{ forms[index].uploading ? '上传中' : '上传图片' }}</text>
        </button>
      </view>
    </view>
    <button class="submit" :disabled="submitting || items.length === 0" @click="submit">提交评价</button>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  createProductCommentsAPI,
  resolveProductMediaUrl,
  uploadCommentImageAPI,
  getUncommentedOrderItemsAPI,
} from '@/apis/product'
import type { OmsOrderItem } from '@/types/order'

const items = ref<OmsOrderItem[]>([])
type CommentForm = { star: number; content: string; pics: string[]; uploading: boolean }

const forms = ref<CommentForm[]>([])
const orderId = ref(0)
const submitting = ref(false)

onLoad(async (options) => {
  orderId.value = Number(options?.orderId || 0)
  if (!orderId.value) return
  try {
    const res = await getUncommentedOrderItemsAPI(orderId.value)
    const targetItemId = Number(options?.orderItemId || 0)
    items.value = (res.data || []).filter((item) => !targetItemId || item.id === targetItemId)
    forms.value = items.value.map(() => ({ star: 5, content: '', pics: [], uploading: false }))
  } catch (error) {
    console.error('加载可评价商品失败:', error)
  }
})

const chooseImages = (index: number) => {
  const form = forms.value[index]
  uni.chooseImage({
    count: 5 - form.pics.length,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async ({ tempFilePaths }) => {
      form.uploading = true
      uni.showLoading({ title: '上传中' })
      try {
        for (const filePath of tempFilePaths) {
          const url = await uploadCommentImageAPI(filePath)
          form.pics.push(url)
        }
      } finally {
        form.uploading = false
        uni.hideLoading()
      }
    },
  })
}

const removeImage = (formIndex: number, picIndex: number) => {
  forms.value[formIndex].pics.splice(picIndex, 1)
}

const previewImage = (formIndex: number, picIndex: number) => {
  const urls = forms.value[formIndex].pics.map(resolveProductMediaUrl)
  uni.previewImage({ current: picIndex, urls })
}

const submit = async () => {
  if (submitting.value || items.value.length === 0) return
  const invalid = forms.value.some((form) => !form.content.trim())
  if (invalid) {
    uni.showToast({ title: '请填写每件商品的评价内容', icon: 'none' })
    return
  }
  if (forms.value.some((form) => form.uploading)) {
    uni.showToast({ title: '请等待图片上传完成', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await createProductCommentsAPI({
      orderId: orderId.value,
      comments: items.value.map((item, index) => ({
        orderItemId: item.id,
        star: forms.value[index].star,
        content: forms.value[index].content.trim(),
        pics: forms.value[index].pics.join(','),
      })),
    })
    uni.showToast({ title: '评价提交成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1000)
  } finally {
    submitting.value = false
  }
}

const formatProductAttr = (json: string) => {
  try {
    return (JSON.parse(json) as { key: string; value: string }[]).map((item) => `${item.key}:${item.value}`).join('；')
  } catch {
    return json || ''
  }
}
</script>

<style lang="scss" scoped>
.page { padding: 24rpx 24rpx 140rpx; background: #f7f7f7; min-height: 100vh; }
.comment-card { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 20rpx; }
.product-row { display: flex; }
.product-row image { width: 120rpx; height: 120rpx; border-radius: 8rpx; }
.product-info { flex: 1; padding-left: 20rpx; display: flex; flex-direction: column; }
.product-name { font-size: 30rpx; color: #333; }
.product-attr { color: #999; font-size: 24rpx; margin-top: 12rpx; }
.star-row { display: flex; align-items: center; margin: 28rpx 0 20rpx; color: #666; font-size: 28rpx; }
.star { color: #ddd; font-size: 48rpx; margin-left: 14rpx; line-height: 1; }
.star.active { color: #ffb400; }
textarea { width: 100%; height: 180rpx; box-sizing: border-box; padding: 18rpx; background: #f8f8f8; border-radius: 8rpx; font-size: 28rpx; }
.image-list { display: flex; flex-wrap: wrap; gap: 16rpx; margin-top: 20rpx; }
.image-item, .add-image { position: relative; width: 140rpx; height: 140rpx; flex: 0 0 140rpx; }
.image-item image { width: 100%; height: 100%; border-radius: 8rpx; }
.remove-image { position: absolute; top: -12rpx; right: -12rpx; width: 40rpx; height: 40rpx; padding: 0; margin: 0; line-height: 36rpx; border-radius: 50%; background: rgba(0, 0, 0, .7); color: #fff; font-size: 30rpx; }
.remove-image::after, .add-image::after { border: 0; }
.add-image { display: flex; flex-direction: column; align-items: center; justify-content: center; margin: 0; padding: 0; border: 1rpx dashed #bbb; border-radius: 8rpx; background: #fafafa; color: #777; font-size: 22rpx; line-height: 1.4; }
.plus { font-size: 54rpx; line-height: 1; font-weight: 300; }
.submit { position: fixed; left: 24rpx; right: 24rpx; bottom: 30rpx; background: #fa436a; color: #fff; border-radius: 50rpx; }
.empty { text-align: center; color: #999; padding-top: 200rpx; }
</style>
