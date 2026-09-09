import { http } from '@/utils/http'
import type { AssistantChatRequest, AssistantChatResponse } from '@/types/assistant'

/** 发送商城智能客服消息 */
export const sendAssistantMessageAPI = (data: AssistantChatRequest) =>
  http<AssistantChatResponse>({
    method: 'POST',
    url: '/assistant/chat',
    data,
    timeout: 35 * 1000,
  })
