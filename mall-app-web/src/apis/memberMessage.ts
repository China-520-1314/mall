import { http } from '@/utils/http'
import type { CommonPage } from '@/types/common'

export type MemberMessage = {
  id: number
  orderId?: number | null
  title: string
  content: string
  readStatus: number
  createTime: string
}

export const getMemberMessageListAPI = (params?: { pageNum?: number; pageSize?: number }) =>
  http<CommonPage<MemberMessage>>({
    method: 'GET',
    url: '/member/message/list',
    params,
  })

export const markMemberMessageReadAPI = (id: number) =>
  http({
    method: 'POST',
    url: `/member/message/read/${id}`,
  })
