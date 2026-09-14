<template>
  <view class="page">
    <view class="form-card">
      <view class="input-item">
        <text class="label">邮箱验证码</text>
        <input v-model="form.authCode" type="number" maxlength="6" placeholder="请输入6位验证码" />
        <button size="mini" :disabled="sending || countdown > 0" @click="sendCode">{{ countdown > 0 ? `${countdown}秒后重试` : sending ? '发送中' : '获取验证码' }}</button>
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
    <text class="tip">验证码发送至当前账号绑定的QQ邮箱，5分钟内有效，同一邮箱10秒内只能发送一次。修改成功后需要重新登录。</text>
    <button class="submit" :disabled="submitting" @click="submit">确认修改</button>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { changePasswordAPI, sendChangePasswordEmailCodeAPI } from '@/apis/member'
import { useMemberStore } from '@/stores/member'

const memberStore = useMemberStore()
const submitting = ref(false)
const form = reactive({ newPassword: '', confirmPassword: '', authCode: '' })
const sending = ref(false)
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | undefined
onUnmounted(() => { if (timer) clearInterval(timer) })
const sendCode = async () => {
  if (sending.value || countdown.value > 0) return
  sending.value = true
  try {
    const result = await sendChangePasswordEmailCodeAPI()
    form.authCode = ''
    const until = Date.now() + result.data.cooldownSeconds * 1000
    const tick = () => { countdown.value = Math.max(0, Math.ceil((until - Date.now()) / 1000)) }
    if (timer) clearInterval(timer)
    tick()
    timer = setInterval(tick, 1000)
    uni.showToast({ title: '验证码已发送至绑定邮箱', icon: 'none' })
  } finally { sending.value = false }
}
const passwordPattern = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,20}$/

onLoad(() => {
  if (!memberStore.hasLogin) {
    uni.redirectTo({ url: '/pages/public/login' })
  }
})

const submit = async () => {
  if (submitting.value) return
  if (!/^\d{6}$/.test(form.authCode)) {
    uni.showToast({ title: '请输入6位邮箱验证码', icon: 'none' })
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
