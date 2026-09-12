/**
 * 用户基本信息
 */
export type MemberInfo = {
  /** 用户ID */
  id: number
  /** 用户名 */
  username: string
  /** 昵称 */
  nickname?: string
  /** 头像 */
  icon?: string
  /** 积分 */
  integration?: number
  /** 成长值 */
  growth?: number
}

/** 登录接口返回结果 */
export type LoginResult = {
  /** Token 前缀（如 "Bearer "） */
  tokenHead: string
  /** 登录凭证 */
  token: string
}

/** 登录请求参数 */
export type LoginParam = {
  /** QQ邮箱账号 */
  email: string
  /** 密码 */
  password: string
}

/** 注册请求参数 */
export type RegisterParam = {
  /** 密码 */
  password: string
  /** 再次输入的密码 */
  confirmPassword: string
  /** 已验证的QQ邮箱 */
  email: string
  /** 邮箱验证码 */
  authCode: string
}

/** 密码重置请求参数 */
export type ResetPasswordParam = {
  email: string
  password: string
  authCode: string
}

/** 邮箱验证码使用场景 */
export type EmailCodePurpose = 'REGISTER' | 'RESET_PASSWORD'

/** 服务端确认发送后的有效期和重新发送间隔（秒）。 */
export type EmailCodeSendResult = {
  email: string
  cooldownSeconds: number
  expiresIn: number
}
