/** 将后端日期转换为时间戳，兼容 H5 和小程序的日期解析差异。 */
export const parseOrderTime = (value?: string | number | null): number => {
  if (value == null || value === '') return 0
  if (typeof value === 'number') return value
  const timestamp = Date.parse(value)
  if (!Number.isNaN(timestamp)) return timestamp
  return Date.parse(value.replace(/-/g, '/'))
}

export const getPaymentRemainingSeconds = (
  paymentExpireTime?: string | number | null,
  now = Date.now(),
): number => {
  const expireAt = parseOrderTime(paymentExpireTime)
  if (!expireAt) return 0
  return Math.max(0, Math.ceil((expireAt - now) / 1000))
}

export const formatPaymentCountdown = (
  paymentExpireTime?: string | number | null,
  now = Date.now(),
): string => {
  const seconds = getPaymentRemainingSeconds(paymentExpireTime, now)
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(remainingSeconds).padStart(2, '0')}`
}
