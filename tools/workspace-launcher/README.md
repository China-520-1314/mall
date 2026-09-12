# Windows 工作区一键启动

将本目录中的 `start-mall.bat`、`start-mall.ps1`、`mall.local.env.example` 和 `scripts` 文件夹复制到工作区根目录，与 `mall-backend`、`mall-app-web`、`mall-admin-web` 三个项目文件夹并列。后端仓库在本机的文件夹名应为 `mall-backend`。

首次使用时将 `mall.local.env.example` 复制为 `mall.local.env`，填写现有数据库账号。已有配置时保留原文件，不覆盖。不要提交真实配置。

依赖 Java 17 或更高版本、Node.js/npm、Maven、MySQL，以及 `runtime/redis` 下的本机 Redis 程序和配置；前后端依赖需要事先安装。启动器不会安装数据库或初始化业务数据。

双击 `start-mall.bat` 启动。执行 `start-mall.bat -CheckOnly` 可仅验证数据库。详细行为与验证记录见 `scripts/启动与验证说明.md`。
