<template>
  <view class="assistant-shell">
    <view v-if="opened" class="assistant-panel" role="dialog" aria-label="Mall 智能客服">
      <view class="assistant-header">
        <view>
          <text class="assistant-title">Mall 智能客服</text>
          <text class="assistant-status">在线为你解答商城问题</text>
        </view>
        <view class="header-actions">
          <button class="icon-button" aria-label="清空对话" @click="clearMessages">清</button>
          <button class="icon-button" aria-label="关闭客服" @click="opened = false">×</button>
        </view>
      </view>

      <scroll-view class="message-list" scroll-y :scroll-into-view="lastMessageId">
        <view
          v-for="(item, index) in messages"
          :id="`assistant-message-${index}`"
          :key="index"
          class="message-row"
          :class="item.role"
        >
          <text class="message-bubble">{{ item.content }}</text>
        </view>
        <view v-if="sending" class="message-row assistant">
          <text class="message-bubble">正在查询，请稍候...</text>
        </view>
      </scroll-view>

      <scroll-view class="quick-list" scroll-x>
        <view class="quick-content">
          <button v-for="question in quickQuestions" :key="question" @click="sendMessage(question)">
            {{ question }}
          </button>
        </view>
      </scroll-view>

      <view class="composer">
        <input
          v-model="draft"
          :disabled="sending"
          :maxlength="800"
          confirm-type="send"
          placeholder="请输入你想咨询的问题"
          @confirm="sendMessage()"
        />
        <button :disabled="sending || !draft.trim()" @click="sendMessage()">发送</button>
      </view>
    </view>

    <button class="assistant-trigger" aria-label="打开智能客服" @click="opened = !opened">
      <text class="trigger-mark">AI</text>
      <text class="trigger-label">客服</text>
    </button>
  </view>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { sendAssistantMessageAPI } from '@/apis/assistant'
import type { AssistantHistoryMessage } from '@/types/assistant'

const welcomeMessage = '你好，我是 Mall 智能客服。商品、订单、物流、售后或账号问题都可以问我。'
const opened = ref(false)
const draft = ref('')
const sending = ref(false)
const messages = ref<AssistantHistoryMessage[]>([
  { role: 'assistant', content: welcomeMessage },
])
const quickQuestions = ['怎么查找商品？', '在哪里查看订单？', '如何申请售后？']
const lastMessageId = computed(() => `assistant-message-${Math.max(0, messages.value.length - 1)}`)

const clearMessages = () => {
  messages.value = [{ role: 'assistant', content: welcomeMessage }]
}

const sendMessage = async (question?: string) => {
  const content = (question || draft.value).trim()
  if (!content || sending.value) return

  const history = messages.value.slice(-8)
  messages.value.push({ role: 'user', content })
  draft.value = ''
  sending.value = true
  await nextTick()

  try {
    const result = await sendAssistantMessageAPI({ message: content, history })
    messages.value.push({
      role: 'assistant',
      content: result.data?.reply || '暂时没有查到答案，请稍后再试。',
    })
  } catch {
    messages.value.push({
      role: 'assistant',
      content: '客服服务暂时无法连接，请检查网络后重试。',
    })
  } finally {
    sending.value = false
  }
}
</script>

<style lang="scss" scoped>
.assistant-shell {
  position: fixed;
  right: 24rpx;
  bottom: calc(126rpx + env(safe-area-inset-bottom));
  z-index: 10000;
}

.assistant-trigger {
  display: flex;
  width: 104rpx;
  height: 104rpx;
  margin: 0 0 0 auto;
  padding: 0;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 4rpx solid #fff;
  border-radius: 50%;
  background: #fa436a;
  box-shadow: 0 10rpx 28rpx rgba(47, 55, 70, 0.24);
  color: #fff;
  line-height: 1;

  &::after {
    border: 0;
  }
}

.trigger-mark {
  font-size: 30rpx;
  font-weight: 700;
}

.trigger-label {
  margin-top: 7rpx;
  font-size: 20rpx;
}

.assistant-panel {
  width: min(680rpx, calc(100vw - 48rpx));
  height: min(850rpx, calc(100vh - 260rpx));
  min-height: 620rpx;
  margin-bottom: 18rpx;
  overflow: hidden;
  border: 1rpx solid #e7e9ee;
  border-radius: 16rpx;
  background: #fff;
  box-shadow: 0 18rpx 70rpx rgba(31, 36, 48, 0.24);
}

.assistant-header {
  display: flex;
  height: 116rpx;
  padding: 0 24rpx;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1rpx solid #eceef2;
  background: #fff;
}

.assistant-title,
.assistant-status {
  display: block;
  letter-spacing: 0;
}

.assistant-title {
  color: #262a33;
  font-size: 30rpx;
  font-weight: 600;
}

.assistant-status {
  margin-top: 8rpx;
  color: #7b8190;
  font-size: 22rpx;
}

.header-actions {
  display: flex;
  gap: 12rpx;
}

.icon-button {
  width: 58rpx;
  height: 58rpx;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: #f2f3f5;
  color: #4b5060;
  font-size: 24rpx;
  line-height: 58rpx;

  &::after {
    border: 0;
  }
}

.message-list {
  height: calc(100% - 260rpx);
  padding: 24rpx;
  box-sizing: border-box;
  background: #f6f7f9;
}

.message-row {
  display: flex;
  margin-bottom: 20rpx;

  &.user {
    justify-content: flex-end;
  }
}

.message-bubble {
  max-width: 78%;
  padding: 18rpx 22rpx;
  border-radius: 12rpx;
  background: #fff;
  color: #363b47;
  font-size: 26rpx;
  line-height: 1.55;
  word-break: break-word;
}

.user .message-bubble {
  background: #fa436a;
  color: #fff;
}

.quick-list {
  height: 66rpx;
  border-top: 1rpx solid #eceef2;
  background: #fff;
  white-space: nowrap;
}

.quick-content {
  display: inline-flex;
  gap: 12rpx;
  padding: 10rpx 20rpx;

  button {
    height: 46rpx;
    margin: 0;
    padding: 0 18rpx;
    border: 1rpx solid #fa436a;
    border-radius: 23rpx;
    background: #fff;
    color: #d9365a;
    font-size: 22rpx;
    line-height: 44rpx;

    &::after {
      border: 0;
    }
  }
}

.composer {
  display: flex;
  height: 78rpx;
  padding: 12rpx 16rpx;
  align-items: center;
  gap: 12rpx;
  border-top: 1rpx solid #eceef2;
  background: #fff;

  input {
    min-width: 0;
    height: 62rpx;
    padding: 0 20rpx;
    flex: 1;
    border-radius: 8rpx;
    background: #f2f3f5;
    color: #303440;
    font-size: 25rpx;
  }

  button {
    width: 112rpx;
    height: 62rpx;
    margin: 0;
    padding: 0;
    border-radius: 8rpx;
    background: #fa436a;
    color: #fff;
    font-size: 25rpx;
    line-height: 62rpx;

    &::after {
      border: 0;
    }

    &[disabled] {
      background: #d6d8dd;
      color: #fff;
    }
  }
}
</style>
