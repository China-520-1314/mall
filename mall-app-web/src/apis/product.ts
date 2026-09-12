import { http } from '@/utils/http'
import type { CommonPage, CommonResult } from '@/types/common'
import type {
  PmsProduct,
  PmsComment,
  ProductCommentBatchParam,
  ProductCommentSummary,
  PmsCommentReply,
  ProductCommentLikeResult,
  MyProductComment,
  ReceivedCommentReply,
} from '@/types/product'
import type { CategoryTreeNode, ProductListParam, PmsPortalProductDetail } from '@/types/product'
import type { OmsOrderItem } from '@/types/order'

/** 商品分类树 */
export const getCategoryTreeAPI = () => {
  return http<CategoryTreeNode[]>({
    method: 'GET',
    url: '/product/categoryTreeList',
  })
}

/** 商品列表搜索 */
export const searchProductListAPI = (params: ProductListParam) => {
  return http<CommonPage<PmsProduct>>({
    method: 'GET',
    url: '/product/search',
    params,
  })
}

/** 商品详情 */
export const getProductDetailAPI = (id: number) => {
  return http<PmsPortalProductDetail>({
    method: 'GET',
    url: `/product/detail/${id}`,
  })
}

/** 商品评价列表 */
export const getProductCommentsAPI = (productId: number, pageNum = 1, pageSize = 10) => {
  return http<CommonPage<PmsComment>>({
    method: 'GET',
    url: `/product/${productId}/comments`,
    params: { pageNum, pageSize },
  })
}

/** 商品评价统计 */
export const getProductCommentSummaryAPI = (productId: number) => {
  return http<ProductCommentSummary>({
    method: 'GET',
    url: `/product/${productId}/commentSummary`,
  })
}

/** 按订单批量提交评价 */
export const createProductCommentsAPI = (data: ProductCommentBatchParam) => {
  return http({ method: 'POST', url: '/product/comments', data })
}

/** 上传评价图片 */
export const uploadCommentImageAPI = (filePath: string) => {
  return new Promise<string>((resolve, reject) => {
    uni.uploadFile({
      url: '/product/comment/image',
      filePath,
      name: 'file',
      success(response) {
        try {
          const result = JSON.parse(response.data) as CommonResult<{ url: string }>
          if (response.statusCode >= 200 && response.statusCode < 300 && result.code === 200) {
            resolve(result.data.url)
            return
          }
          uni.showToast({ title: result.message || '图片上传失败', icon: 'none' })
          reject(new Error(result.message || '图片上传失败'))
        } catch (error) {
          uni.showToast({ title: '图片上传响应异常', icon: 'none' })
          reject(error)
        }
      },
      fail(error) {
        uni.showToast({ title: '图片上传失败', icon: 'none' })
        reject(error)
      },
    })
  })
}

/** 获取评价回复 */
export const getCommentRepliesAPI = (commentId: number, pageNum = 1, pageSize = 10) =>
  http<CommonPage<PmsCommentReply>>({ method: 'GET', url: `/product/comments/${commentId}/replies`, params: { pageNum, pageSize } })

/** 回复评价 */
export const createCommentReplyAPI = (commentId: number, content: string) =>
  http({ method: 'POST', url: `/product/comments/${commentId}/replies`, data: { content } })

/** 删除自己的回复 */
export const deleteCommentReplyAPI = (replyId: number) =>
  http({ method: 'DELETE', url: `/product/comments/replies/${replyId}` })

/** 点赞或取消点赞评价 */
export const toggleCommentLikeAPI = (commentId: number) =>
  http<ProductCommentLikeResult>({ method: 'POST', url: `/product/comments/${commentId}/like` })

/** 删除自己的评价 */
export const deleteCommentAPI = (commentId: number) =>
  http({ method: 'DELETE', url: `/product/comments/${commentId}` })

/** 当前商品最近一笔可评价的已完成购买 */
export const getCommentPurchaseAPI = (productId: number) =>
  http<{ orderId: number; orderItemId: number } | null>({ method: 'GET', url: `/product/${productId}/commentPurchase` })

/** 当前订单尚未评价的商品，后端校验订单归属和完成状态 */
export const getUncommentedOrderItemsAPI = (orderId: number) =>
  http<OmsOrderItem[]>({ method: 'GET', url: `/product/comment/order/${orderId}/items` })

/** 当前用户发布的评价。 */
export const getMyProductCommentsAPI = (pageNum = 1, pageSize = 10) =>
  http<CommonPage<MyProductComment>>({
    method: 'GET',
    url: '/product/comments/mine',
    params: { pageNum, pageSize },
  })

/** 他人对当前用户评价的回复。 */
export const getReceivedCommentRepliesAPI = (pageNum = 1, pageSize = 10) =>
  http<CommonPage<ReceivedCommentReply>>({
    method: 'GET',
    url: '/product/comments/replies/mine',
    params: { pageNum, pageSize },
  })

/** 将后端相对资源地址转换为浏览器可访问地址 */
export const resolveProductMediaUrl = (url: string) =>
  url.startsWith('/') ? `${import.meta.env.VITE_API_BASE_URL}${url}` : url
