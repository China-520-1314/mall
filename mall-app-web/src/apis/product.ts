import { http } from '@/utils/http'
import type { CommonPage, CommonResult } from '@/types/common'
import type {
  PmsProduct,
  PmsComment,
  ProductCommentBatchParam,
  ProductCommentSummary,
} from '@/types/product'
import type { CategoryTreeNode, ProductListParam, PmsPortalProductDetail } from '@/types/product'

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

/** 将后端相对资源地址转换为浏览器可访问地址 */
export const resolveProductMediaUrl = (url: string) =>
  url.startsWith('/') ? `${import.meta.env.VITE_API_BASE_URL}${url}` : url
