<template>
  <view class="container">
    <view class="left-bottom-sign"></view>
    <view class="back-btn yticon icon-zuojiantou-up" @click="navBack"></view>
    <view class="right-top-sign"></view>
    <!-- 设置白色背景防止软键盘把下部绝对定位元素顶上来盖住输入框等 -->
    <view class="wrapper">
      <view class="left-top-sign">LOGIN</view>
      <view class="welcome">欢迎回来！</view>
      <view class="input-content">
        <view class="input-item">
          <text class="tit">QQ邮箱账号</text>
          <view class="qq-email-input">
            <input type="number" v-model="qqNumber" placeholder="请输入QQ号" :maxlength="12" />
            <text class="email-suffix">@qq.com</text>
          </view>
        </view>
        <view class="input-item">
          <text class="tit">密码</text>
          <input
            v-model="password"
            placeholder="8-18位不含特殊字符的数字、字母组合"
            placeholder-class="input-empty"
            :maxlength="20"
            password
            @confirm="toLogin"
          />
        </view>
      </view>
      <button class="confirm-btn" @click="toLogin" :disabled="logining"> 登录 </button>
      <button class="confirm-btn2" @click="toRegist('register')">注册新账号</button>
      <view class="forget-section" @click="toRegist('reset')">忘记密码？使用邮箱验证码登录</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useMemberStore } from '@/stores/member'

// 获取会员store
const memberStore = useMemberStore()

// 登录页只输入 QQ 号，提交时自动补全邮箱后缀。
const storedEmail = String(uni.getStorageSync('email') || '')
const qqNumber = ref(storedEmail.replace(/@qq\.com$/i, '').replace(/\D/g, '').slice(0, 12))
// 登录密码
const password = ref('')
// 登录加载状态
const logining = ref(false)
const isValidQQNumber = (value: string) => /^[1-9][0-9]{4,11}$/.test(value.trim())

// 登录处理
const toLogin = async () => {
  const normalizedQQ = qqNumber.value.trim()
  if (!isValidQQNumber(normalizedQQ)) {
    uni.showToast({
      title: '请输入正确的QQ号',
      icon: 'none',
    })
    return
  }
  if (!password.value) {
    uni.showToast({ title: '请输入密码', icon: 'none' })
    return
  }

  logining.value = true

  try {
    await memberStore.memberLogin(`${normalizedQQ}@qq.com`, password.value)

    uni.showToast({
      title: '登录成功',
      icon: 'success',
    })

    setTimeout(() => {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        uni.navigateBack()
      } else {
        uni.switchTab({ url: '/pages/index/index' })
      }
    }, 1000)
  } catch (error) {
    uni.showToast({
      title: '登录失败，请检查QQ邮箱和密码',
      icon: 'none',
    })
  } finally {
    logining.value = false
  }
}

// 返回上一页
const navBack = () => {
  uni.navigateBack()
}

// 跳转到注册或密码找回页
const toRegist = (mode: 'register' | 'reset') => {
  uni.navigateTo({ url: `/pages/public/register?mode=${mode}` })
}
</script>

<style lang="scss">
page {
  background: #fff;
}

.container {
  padding-top: 115px;
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
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
    /* background: pink; */
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
  height: 120rpx;
  border-radius: 4px;
  margin-bottom: 50rpx;

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
  margin-top: 70rpx;
  background: $uni-color-primary;
  color: #fff;
  font-size: $font-lg;

  &:after {
    border-radius: 100px;
  }
}

.confirm-btn2 {
  width: 630rpx;
  height: 76rpx;
  line-height: 76rpx;
  border-radius: 50px;
  margin-top: 40rpx;
  border: 1rpx solid $uni-color-primary;
  background: #fff;
  color: $uni-color-primary;
  font-size: $font-lg;

  &:after {
    border-radius: 100px;
  }
}

.forget-section {
  font-size: $font-sm + 2rpx;
  color: $font-color-spec;
  text-align: center;
  margin-top: 40rpx;
}

</style>
