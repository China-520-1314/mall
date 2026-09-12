# 改进版运行与 QQ 邮箱配置

本仓库同时包含 Java 后端、用户端 `mall-app-web`、管理端 `mall-admin-web`，无需另行克隆两个前端。

## 已包含的功能

- QQ 邮箱作为账号；登录和注册输入 QQ 号码时自动补全 `@qq.com`。
- 注册时发送真实邮件验证码、两次输入密码；支持邮件验证码找回密码和登录后修改密码。
- 个性化商品推荐、分类搜索、商品分享、管理端站内信与用户端消息通知。
- 立即购买、购物车下单、30 分钟待付款倒计时、演示支付、取消订单、确认收货。
- 购买并确认收货后可按订单项评价，支持评分、图片、回复、点赞和删除自己的内容。
- “我的 → 我的评价”展示自己的购买评价及他人的回复。

当前微信/支付宝选择页面是项目演示支付，点击后更新本地订单状态，不会发生真实扣款或退款。
未付款和已付款未发货订单可取消；已发货订单不可直接取消。

## 一、准备环境和数据库

建议使用 JDK 17、Node.js 22.12 或更高版本、MySQL 8 和 Redis。管理端登录等功能需要 Redis。
仓库提供 Maven Wrapper；首次构建需要联网下载 Maven 和依赖。
用户端后端的 `dev` 配置已关闭 RabbitMQ 消费和延迟取消消息，订单超时由定时任务处理。
MongoDB 未运行时部分功能使用现有本地降级逻辑；完整部署仍应按原项目文档配置相关服务。

新环境：创建字符集为 `utf8mb4` 的 `mall` 数据库，使用数据库工具导入 `document/sql/mall.sql`。
该脚本包含删表语句，仅用于新库初始化，不要在有业务数据的数据库上重复导入。

旧环境：先备份并检查表结构，再按缺少的结构执行升级脚本：

| 脚本 | 用途 |
| --- | --- |
| `document/sql/mall_member_email.sql` | 邮箱字段及唯一索引 |
| `document/sql/mall_comment_order_binding.sql` | 评价绑定会员、订单与订单项 |
| `document/sql/mall_comment_interaction.sql` | 评价回复作者和点赞表 |
| `document/sql/mall_member_message.sql` | 站内信表 |

最新版 `mall.sql` 已含相应结构；这些结构升级脚本不要重复执行。
扩充的商品数据和来源说明位于 `document/sql/mall_catalog_mi_20260911.sql` 与 `document/catalog/`，
配套图片已包含在 `mall-portal/src/main/resources/static/catalog/`。
如需导入扩充商品，按 `scripts/catalog/import_catalog.py` 的连接配置导入商品脚本；不要将其当作数据库初始化脚本。

## 二、开启 QQ 邮箱 SMTP

1. 登录用于发送验证码的 QQ 邮箱网页版，打开“设置 → 账号”。
2. 找到 POP3/IMAP/SMTP 服务，开启支持 SMTP 的服务，按邮箱提示完成身份验证。
3. 生成并保存 SMTP 授权码。后端需要的是这个授权码，不是 QQ 登录密码。
4. 在本机 PowerShell 中配置发送邮箱、授权码和数据库密码。

从项目根目录复制配置示例：

```powershell
Copy-Item .\scripts\configure-local.example.ps1 .\scripts\configure-local.ps1
notepad .\scripts\configure-local.ps1
```

将示例占位内容改为自己的值。等效环境变量如下：

```powershell
$env:MALL_DB_PASSWORD = '你的MySQL密码'
$env:QQ_MAIL_USERNAME = '你的QQ号码@qq.com'
$env:QQ_MAIL_AUTH_CODE = '你的SMTP授权码'
```

发送方是 `QQ_MAIL_USERNAME`，接收方是用户在注册或找回密码页面填写的 QQ 邮箱，两者可以不同。
后端已配置 `smtp.qq.com:465`、SSL 和 SMTP 身份验证，不需要接入阿里云短信。
本地配置文件被 Git 忽略，仓库只发布示例；不要把授权码填到 `VITE_` 变量或前端源码中。
PowerShell 环境变量仅对当前窗口及其启动的程序生效；新开窗口后需再次加载本地配置。

## 三、启动商城后端

在项目根目录运行：

```powershell
. .\scripts\configure-local.ps1
.\mvnw.cmd -pl mall-portal -am install -DskipTests -Ddocker.skip=true
java -jar .\mall-portal\target\mall-portal-1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

访问 `http://localhost:8085/home/content` 可检查后端。
数据库地址默认 `localhost:3306/mall`、账号默认 `root`。
其他配置可以通过 `MALL_DATASOURCE_URL`、`MALL_DATASOURCE_USERNAME`、`MALL_DATASOURCE_PASSWORD` 覆盖。
上述直接启动方式不需要配置 AI 密钥；智能客服没有密钥时使用本地商城问答。
根目录旧版 `run-mall-portal.ps1` 有 AI 密钥检查，单独运行商城建议使用上述命令。

## 四、启动用户端

另开 PowerShell，在 `mall-app-web` 目录运行：

```powershell
npm ci
npm run dev:h5 -- --host 127.0.0.1 --port 5173
```

访问 `http://localhost:5173/#/`。开发配置 `.env.development` 已指向本机 `8085` 后端。
如果使用 `npm run build:h5` 打包后本地预览，需在 `mall-app-web/.env.production.local` 写入：

```dotenv
VITE_API_BASE_URL=http://localhost:8085
```

该文件只留在本机，否则生产配置默认请求原项目的演示接口，无法使用本次新增接口。

## 五、启动管理端（可选）

在根目录的另一个 PowerShell 窗口加载数据库配置并启动管理后端：

```powershell
. .\scripts\configure-local.ps1
.\mvnw.cmd -pl mall-admin -am install -DskipTests -Ddocker.skip=true
java -jar .\mall-admin\target\mall-admin-1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

管理后端默认为 `8080`。在 `mall-admin-web` 目录的另一个窗口运行：

```powershell
npm ci
npm run dev -- --host 127.0.0.1 --port 5174
```

访问 `http://localhost:5174/#/`，`.env.development` 已指向本机管理后端。

## 六、QQ 验证码的规则与排查

验证码有效 5 分钟；同一邮箱成功发送后 10 秒内不能再次发送，每小时最多成功发送 5 次。
最多输错 5 次后验证码失效。注册和找回密码使用不同用途的验证码，不能混用；成功验证后只能消费一次。
验证码保存在单个后端进程内存，重启后失效；多实例部署需要改为 Redis 等共享存储。

- 提示未配置邮箱：确认已在启动 Java 的同一个窗口加载 `configure-local.ps1`。
- 发送失败：核对完整发送邮箱和 SMTP 授权码，确认 SMTP 已开启，以及网络允许访问 465 端口。
- 没收到邮件：检查接收邮箱输入、垃圾邮件箱和后端错误日志；不要连续重发触发每小时限额。
- 请求到演示服务器或报接口不存在：确认用户端 API 地址指向自己运行的 `8085` 后端。

核心源码：

| 内容 | 文件 |
| --- | --- |
| 发信、验证码、频率限制 | `mall-portal/src/main/java/com/macro/mall/portal/service/impl/EmailVerificationServiceImpl.java` |
| 注册、找回和修改密码接口 | `mall-portal/src/main/java/com/macro/mall/portal/controller/UmsMemberController.java` |
| 会员账号逻辑 | `mall-portal/src/main/java/com/macro/mall/portal/service/impl/UmsMemberServiceImpl.java` |
| SMTP 和验证码配置 | `mall-portal/src/main/resources/application.yml` |
| 注册与找回密码页面 | `mall-app-web/src/pages/public/register.vue` |
| 登录页面 | `mall-app-web/src/pages/public/login.vue` |
| 修改密码页面 | `mall-app-web/src/pages/set/changePassword.vue` |
| 前端邮箱接口 | `mall-app-web/src/apis/member.ts` |

相关自动化检查：

```powershell
.\mvnw.cmd -pl mall-portal -am '-DskipTests=false' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=EmailVerificationServiceImplTest,OmsPortalOrderServiceImplTest,PmsPortalProductServiceImplTest,ProductCommentControllerTest,ProductCommentMapperTest' test
```

这些测试不发送真实邮件，实际邮件收取需要运行后端并在注册页面手动验证。
