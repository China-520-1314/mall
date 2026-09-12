<template>
  <view class="page">
    <view class="form-card">
      <view class="input-item">
        <text class="label">当前密码</text>
        <input v-model="form.oldPassword" password maxlength="20" placeholder="请输入当前密码" />
      </view>
      <view class="input-item">
        <text class="label">新密码</text>
        <input v-model="form.newPassword" password maxlength="20" placeholder="8-20位字母和数字组合" />
      </view>
      <view class="input-item">
        <text class="label">确认新密码</text>
        <input v-model="form.confirmPassword" password maxlength="20" placeholder="请再次输入新密码" />
      </view>
    </view>
    <text class="tip">修改成功后需要重新登录。</text>
    <button class="submit" :disabled="submitting" @click="submit">确认修改</button>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { changePasswordAPI } from '@/apis/member'
import { useMemberStore } from '@/stores/member'

const memberStore = useMemberStore()
const submitting = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordPattern = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,20}$/

onLoad(() => {
  if (!memberStore.hasLogin) {
    uni.redirectTo({ url: '/pages/public/login' })
  }
})

const submit = async () => {
  if (!form.oldPassword) {
    uni.showToast({ title: '请输入当前密码', icon: 'none' })
    return
  }
  if (!passwordPattern.test(form.newPassword)) {
    uni.showToast({ title: '新密码须为8到20位字母和数字组合', icon: 'none' })
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    uni.showToast({ title: '两次输入的新密码不一致', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await changePasswordAPI(form)
    memberStore.memberLogout()
    uni.showToast({ title: '密码修改成功，请重新登录', icon: 'success' })
    setTimeout(() => uni.redirectTo({ url: '/pages/public/login' }), 1000)
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
page { background: #f7f7f7; }
.page { min-height: 100vh; padding: 24rpx; }
.form-card { padding: 0 24rpx; border-radius: 14rpx; background: #fff; }
.input-item { display: flex; align-items: center; min-height: 104rpx; border-bottom: 1rpx solid #eee; }
.input-item:last-child { border-bottom: 0; }
.label { width: 180rpx; color: #303133; font-size: 28rpx; }
input { flex: 1; color: #303133; font-size: 28rpx; }
.tip { display: block; margin: 24rpx 8rpx; color: #909399; font-size: 24rpx; }
.submit { margin-top: 36rpx; border-radius: 48rpx; background: #fa436a; color: #fff; }
.submit[disabled] { opacity: .6; }
</style>
