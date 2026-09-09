export type AssistantMessageRole = 'user' | 'assistant'

export type AssistantHistoryMessage = {
  role: AssistantMessageRole
  content: string
}

export type AssistantChatRequest = {
  message: string
  history: AssistantHistoryMessage[]
}

export type AssistantChatResponse = {
  reply: string
  fallback: boolean
}
