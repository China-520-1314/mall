<template>
  <view class="container">
    <view class="left-bottom-sign"></view>
    <view class="back-btn yticon icon-zuojiantou-up" @click="navBack"></view>
    <view class="right-top-sign"></view>

    <view class="wrapper">
      <view class="left-top-sign">{{ pageMode === 'register' ? 'REGISTER' : 'LOGIN' }}</view>
      <view class="welcome">{{ pageMode === 'register' ? '注册账号！' : '验证码登录' }}</view>
      <view class="input-content">
        <view class="input-item">
          <text class="tit">QQ邮箱账号</text>
          <view class="qq-email-input">
            <input
              type="text"
              inputmode="numeric"
              v-model="formData.email"
              @blur="normalizeEmailInput"
              placeholder="输入QQ号或粘贴QQ邮箱"
              :maxlength="32"
              :disabled="sendingCode || submitting"
            />
            <text v-if="!formData.email.trim().toLowerCase().endsWith('@qq.com')" class="email-suffix">@qq.com</text>
          </view>
        </view>
        <view class="email-code-hint" :class="{ 'is-error': sendError }" aria-live="polite">
          <text v-if="sendError">{{ sendError }}</text>
          <text v-else-if="sentToEmail">
            验证码已发送至 {{ sentToEmail }}，{{ codeValidityMinutes }}分钟内有效。请使用最新验证码，未收到时请查看垃圾邮件。
          </text>
          <text v-else>输入QQ号后自动补全@qq.com。验证码10秒后可重新发送，每小时最多5次。</text>
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
        <view v-if="pageMode === 'register'" class="input-item">
          <text v-if="pageMode === 'register'" class="tit">密码</text>
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
        {{ pageMode === 'register' ? '注册' : '验证码登录' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { registerAPI, sendEmailCodeAPI } from '@/apis/member'
import type { RegisterParam, EmailCodePurpose, EmailCodeSendResult } from '@/types/member'
import { useMemberStore } from '@/stores/member'

// ===== 页面数据 =====
// 页面模式：register=注册表单, reset=邮箱验证码登录
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
const sentToEmail = ref('')
const codeValidityMinutes = ref(5)
const sendError = ref('')
let countdownTimer: ReturnType<typeof setInterval> | null = null
const memberStore = useMemberStore()
const emailStateKey = 'mall-email-code-state'
const emailDraftKey = 'mall-email-code-account'
type EmailSendState = {
  cooldownUntil: number
  codeExpiresAt: number
  validitySeconds: number
  purpose: EmailCodePurpose
}
let memoryStates: Record<string, EmailSendState> = {}

// 只保存邮箱及过期时间，密码与验证码始终不写入本地存储。
const readStates = () => {
  try {
    const saved = uni.getStorageSync(emailStateKey)
    if (saved && typeof saved === 'object') memoryStates = saved
  } catch {
    // 存储不可用时仍可在当前页面使用倒计时。
  }
  const now = Date.now()
  Object.keys(memoryStates).forEach((email) => {
    const state = memoryStates[email]
    if (!state || (state.cooldownUntil <= now && state.codeExpiresAt <= now)) delete memoryStates[email]
  })
  return memoryStates
}

const saveState = (email: string, state: EmailSendState) => {
  const states = readStates()
  states[email] = state
  try {
    uni.setStorageSync(emailStateKey, states)
    uni.setStorageSync(emailDraftKey, email.replace(/@qq\.com$/i, ''))
  } catch {
    // 存储失败不影响后端已确认的发送成功结果。
  }
}

// ===== 生命周期 =====
// 页面加载时根据参数设置模式
onLoad((options) => {
  if (options?.mode === 'register' || options?.mode === 'reset') {
    pageMode.value = options.mode
  } else {
    pageMode.value = 'register'
  }
  try {
    const savedEmail = uni.getStorageSync(emailDraftKey)
    if (typeof savedEmail === 'string' && isValidQQNumber(savedEmail)) formData.value.email = savedEmail
  } catch {
    // 无可恢复账号时保留空输入框。
  }
  restoreCountdown()
})

onShow(() => restoreCountdown())

onUnload(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

const normalizeQQNumber = (value: string) => value.trim().replace(/@qq\.com$/i, '')
const isValidQQNumber = (value: string) => /^[1-9][0-9]{4,11}$/.test(normalizeQQNumber(value))
const getQQEmail = () => `${normalizeQQNumber(formData.value.email)}@qq.com`
const normalizeEmailInput = () => {
  formData.value.email = normalizeQQNumber(formData.value.email)
}

const currentPurpose = (): EmailCodePurpose =>
  pageMode.value === 'reset' ? 'RESET_PASSWORD' : 'REGISTER'

const updateCountdown = () => {
  const state = isValidQQNumber(formData.value.email) ? readStates()[getQQEmail()] : undefined
  countdown.value = Math.max(0, Math.ceil(((state?.cooldownUntil || 0) - Date.now()) / 1000))
  sentToEmail.value = state && state.codeExpiresAt > Date.now() && state.purpose === currentPurpose()
    ? getQQEmail() : ''
  codeValidityMinutes.value = Math.ceil((state?.validitySeconds || 300) / 60)
}

const restoreCountdown = () => {
  updateCountdown()
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(updateCountdown, 1000)
}

watch(() => formData.value.email, () => {
  formData.value.authCode = ''
  sendError.value = ''
  updateCountdown()
})

const handleSendEmailCode = async () => {
  updateCountdown()
  if (sendingCode.value || countdown.value > 0) return
  if (!isValidQQNumber(formData.value.email)) {
    uni.showToast({ title: '请输入正确的QQ号', icon: 'none' })
    return
  }
  const email = getQQEmail()
  const purpose = currentPurpose()
  sendingCode.value = true
  sendError.value = ''
  try {
    const response = await sendEmailCodeAPI(email, purpose)
    saveState(email, {
      cooldownUntil: Date.now() + response.data.cooldownSeconds * 1000,
      codeExpiresAt: Date.now() + response.data.expiresIn * 1000,
      validitySeconds: response.data.expiresIn,
      purpose,
    })
    formData.value.authCode = ''
    restoreCountdown()
    uni.showToast({ title: '验证码已发送', icon: 'success' })
  } catch (error) {
    const result = (error as { data?: { code?: number; message?: string; data?: EmailCodeSendResult } })?.data
    sendError.value = result?.message || '未确认发送结果，请先检查收件箱和垃圾邮件，稍后重试。'
    if (result?.code === 429 && result.data) {
      const previous = readStates()[email]
      saveState(email, {
        cooldownUntil: Date.now() + result.data.cooldownSeconds * 1000,
        codeExpiresAt: previous?.codeExpiresAt || 0,
        validitySeconds: previous?.validitySeconds || 300,
        purpose: previous?.purpose || purpose,
      })
      restoreCountdown()
    }
  } finally {
    sendingCode.value = false
  }
}

const handleSubmit = async () => {
  if (submitting.value) return
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
  if (pageMode.value === 'register' && !/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,20}$/.test(password)) {
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
      try {
        await memberStore.memberLogin(email, password)
      } catch {
        uni.showToast({ title: '注册已成功，请前往登录', icon: 'none' })
        setTimeout(() => uni.redirectTo({ url: '/pages/public/login' }), 1500)
        return
      }
      uni.showToast({ title: '注册并登录成功', icon: 'success' })
      setTimeout(() => {
        uni.switchTab({ url: '/pages/user/user' })
      }, 1000)
      return
    } else {
      await memberStore.memberLoginByEmailCode(email, authCode)
      uni.showToast({ title: '验证码登录成功', icon: 'success' })
      setTimeout(() => uni.switchTab({ url: '/pages/index/index' }), 800)
      return
    }
  } catch {
    // HTTP 层已显示具体错误；保留表单供用户修正后重试。
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

.email-code-hint {
  margin: -4rpx 0 24rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #697386;
  word-break: break-all;

  &.is-error {
    color: #c4473e;
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
