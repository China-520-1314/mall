<template>
  <view class="container">
    <view class="left-bottom-sign"></view>
    <view class="back-btn yticon icon-zuojiantou-up" @click="navBack"></view>
    <view class="right-top-sign"></view>

    <view class="wrapper">
      <view class="left-top-sign">{{ pageMode === 'register' ? 'REGISTER' : 'RESET' }}</view>
      <view class="welcome">{{ pageMode === 'register' ? '注册账号！' : '重置密码' }}</view>
      <view class="input-content">
        <view class="input-item">
          <text class="tit">QQ邮箱账号</text>
          <view class="qq-email-input">
            <input
              type="number"
              v-model="formData.email"
              placeholder="请输入QQ号"
              :maxlength="12"
            />
            <text class="email-suffix">@qq.com</text>
          </view>
        </view>
        <view class="input-item">
          <text class="tit">邮箱验证码</text>
          <view class="auth-code-row">
            <input
              type="number"
              v-model="formData.authCode"
              placeholder="请输入6位验证码"
              :maxlength="6"
            />
            <button
              class="get-code-btn"
              :class="{ disabled: countdown > 0 || sendingCode }"
              :disabled="countdown > 0 || sendingCode"
              @click="handleSendEmailCode"
            >
              {{ countdown > 0 ? `${countdown}秒后重试` : sendingCode ? '发送中' : '获取验证码' }}
            </button>
          </view>
        </view>
        <view class="input-item">
          <text class="tit">{{ pageMode === 'register' ? '密码' : '新密码' }}</text>
          <input
            v-model="formData.password"
            placeholder="8-20位字母和数字组合"
            placeholder-class="input-empty"
            :maxlength="20"
            password
          />
        </view>
        <view v-if="pageMode === 'register'" class="input-item">
          <text class="tit">确认密码</text>
          <input
            v-model="formData.confirmPassword"
            placeholder="请再次输入密码"
            placeholder-class="input-empty"
            :maxlength="20"
            password
          />
        </view>
      </view>
      <button class="confirm-btn" @click="handleSubmit" :disabled="submitting">
        {{ pageMode === 'register' ? '注册' : '重置密码' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { registerAPI, resetPasswordAPI, sendEmailCodeAPI } from '@/apis/member'
import type { RegisterParam, EmailCodePurpose } from '@/types/member'
import { useMemberStore } from '@/stores/member'

// ===== 页面数据 =====
// 页面模式：register=注册表单, reset=密码找回
const pageMode = ref<'register' | 'reset'>('register')
// 注册表单数据
const formData = ref<RegisterParam>({
  password: '',
  confirmPassword: '',
  email: '',
  authCode: '',
})
const submitting = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null
const memberStore = useMemberStore()

// ===== 生命周期 =====
// 页面加载时根据参数设置模式
onLoad((options) => {
  if (options?.mode === 'register' || options?.mode === 'reset') {
    pageMode.value = options.mode
  } else {
    pageMode.value = 'register'
  }
})

onUnload(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

const isValidQQNumber = (value: string) => /^[1-9][0-9]{4,11}$/.test(value.trim())
const getQQEmail = () => `${formData.value.email.trim()}@qq.com`

const currentPurpose = (): EmailCodePurpose =>
  pageMode.value === 'reset' ? 'RESET_PASSWORD' : 'REGISTER'

const startCountdown = () => {
  countdown.value = 10
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0 && countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

const handleSendEmailCode = async () => {
  if (!isValidQQNumber(formData.value.email)) {
    uni.showToast({ title: '请输入正确的QQ号', icon: 'none' })
    return
  }
  const email = getQQEmail()
  sendingCode.value = true
  try {
    await sendEmailCodeAPI(email, currentPurpose())
    startCountdown()
    uni.showToast({ title: '验证码已发送', icon: 'success' })
  } finally {
    sendingCode.value = false
  }
}

const handleSubmit = async () => {
  const { password, confirmPassword, authCode } = formData.value
  if (!isValidQQNumber(formData.value.email)) {
    uni.showToast({ title: '请输入正确的QQ号', icon: 'none' })
    return
  }
  const email = getQQEmail()
  if (!/^[0-9]{6}$/.test(authCode)) {
    uni.showToast({ title: '请输入6位邮箱验证码', icon: 'none' })
    return
  }
  if (!/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,20}$/.test(password)) {
    uni.showToast({ title: '密码须为8到20位字母和数字组合', icon: 'none' })
    return
  }
  if (pageMode.value === 'register' && password !== confirmPassword) {
    uni.showToast({ title: '两次输入的密码不一致', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    if (pageMode.value === 'register') {
      await registerAPI({ ...formData.value, email })
      await memberStore.memberLogin(email, password)
      uni.showToast({ title: '注册并登录成功', icon: 'success' })
      setTimeout(() => {
        uni.switchTab({ url: '/pages/user/user' })
      }, 1000)
      return
    } else {
      await resetPasswordAPI({ email, password, authCode })
    }
    uni.showToast({ title: '密码重置成功', icon: 'success' })
    setTimeout(() => {
      uni.redirectTo({ url: '/pages/public/login' })
    }, 1500)
  } finally {
    submitting.value = false
  }
}

// 返回上一页
const navBack = () => {
  uni.navigateBack()
}
</script>

<style lang="scss">
page {
  background: #fff;
}
</style>

<style lang="scss" scoped>
.container {
  padding-top: 115px;
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow-y: auto;
  background: #fff;
}

.wrapper {
  position: relative;
  z-index: 90;
  background: #fff;
  padding-bottom: 40rpx;
}

.back-btn {
  position: absolute;
  left: 40rpx;
  z-index: 9999;
  top: calc(var(--status-bar-height) + 40rpx);
  font-size: 40rpx;
  color: $font-color-dark;
}

.left-top-sign {
  font-size: 120rpx;
  color: $page-color-base;
  position: relative;
  left: -16rpx;
}

.right-top-sign {
  position: absolute;
  top: calc(var(--status-bar-height) + 80rpx);
  right: -30rpx;
  z-index: 95;

  &:before,
  &:after {
    display: block;
    content: '';
    width: 400rpx;
    height: 80rpx;
    background: #b4f3e2;
  }

  &:before {
    transform: rotate(50deg);
    border-radius: 0 50px 0 0;
  }

  &:after {
    position: absolute;
    right: -198rpx;
    top: 0;
    transform: rotate(-50deg);
    border-radius: 50px 0 0 0;
  }
}

.left-bottom-sign {
  position: absolute;
  left: -270rpx;
  bottom: -320rpx;
  border: 100rpx solid #d0d1fd;
  border-radius: 50%;
  padding: 180rpx;
}

.welcome {
  position: relative;
  left: 50rpx;
  top: -90rpx;
  font-size: 46rpx;
  color: #555;
  text-shadow: 1px 0px 1px rgba(0, 0, 0, 0.3);
}

.input-content {
  padding: 0 60rpx;
}

.input-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  padding: 0 30rpx;
  background: $page-color-light;
  min-height: 104rpx;
  border-radius: 4px;
  margin-bottom: 24rpx;

  &:last-child {
    margin-bottom: 0;
  }

  .tit {
    height: 50rpx;
    line-height: 56rpx;
    font-size: $font-sm + 2rpx;
    color: $font-color-base;
  }

  input {
    height: 60rpx;
    font-size: $font-base + 2rpx;
    color: $font-color-dark;
    width: 100%;
  }
}

.auth-code-row {
  display: flex;
  align-items: center;
  width: 100%;

  input {
    flex: 1;
  }

  .get-code-btn {
    flex-shrink: 0;
    width: 200rpx;
    height: 60rpx;
    line-height: 60rpx;
    font-size: 24rpx;
    color: $uni-color-primary;
    background: #fff;
    border: 1rpx solid $uni-color-primary;
    border-radius: 8rpx;
    padding: 0;
    margin: 0;
    margin-left: 20rpx;

    &::after {
      border: none;
    }

    &.disabled {
      color: $font-color-disabled;
      border-color: $font-color-disabled;
    }
  }
}

.qq-email-input {
  display: flex;
  align-items: center;
  width: 100%;
  height: 60rpx;

  input {
    flex: 1;
    min-width: 0;
  }
}

.email-suffix {
  flex-shrink: 0;
  padding-left: 12rpx;
  font-size: $font-base + 2rpx;
  color: $font-color-dark;
}

.confirm-btn {
  width: 630rpx;
  height: 76rpx;
  line-height: 76rpx;
  border-radius: 50px;
  margin-top: 36rpx;
  margin-bottom: 60rpx;
  background: $uni-color-primary;
  color: #fff;
  font-size: $font-lg;

  &:after {
    border-radius: 100px;
  }
}
</style>
