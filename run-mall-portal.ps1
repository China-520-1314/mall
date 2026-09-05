param(
    [ValidateSet('portal', 'assistant')]
    [string]$Mode = 'portal',
    [string]$Profile,
    [string]$DatasourceUrl,
    [string]$DatasourceUsername,
    [string]$DatasourcePassword
)

$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$authPath = Join-Path $env:USERPROFILE '.codex/auth.json'

function Initialize-CiyuanshenProcessKey {
    foreach ($name in @('CIYUANSHEN_TEXT_API_KEY', 'CIYUANSHEN_API_KEY')) {
        if (-not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
            return
        }

        $userValue = [Environment]::GetEnvironmentVariable($name, 'User')
        if (-not [string]::IsNullOrWhiteSpace($userValue)) {
            [Environment]::SetEnvironmentVariable($name, $userValue, 'Process')
            return
        }
    }
}

# 双击启动时资源管理器可能仍持有旧环境，先读取用户级词元神 Key；不使用 OPENAI_API_KEY，避免生图专用 Key 覆盖文字模型 Key。
Initialize-CiyuanshenProcessKey

# 只有两个词元神变量都为空时才读取本地认证。
if ([string]::IsNullOrWhiteSpace($env:CIYUANSHEN_TEXT_API_KEY) -and
    [string]::IsNullOrWhiteSpace($env:CIYUANSHEN_API_KEY) -and
    (Test-Path -LiteralPath $authPath)) {
    $auth = Get-Content -LiteralPath $authPath -Raw | ConvertFrom-Json
    if (-not [string]::IsNullOrWhiteSpace($auth.CIYUANSHEN_TEXT_API_KEY)) {
        $env:CIYUANSHEN_TEXT_API_KEY = [string]$auth.CIYUANSHEN_TEXT_API_KEY
    } elseif (-not [string]::IsNullOrWhiteSpace($auth.CIYUANSHEN_API_KEY)) {
        $env:CIYUANSHEN_API_KEY = [string]$auth.CIYUANSHEN_API_KEY
    }
}

if (-not [string]::IsNullOrWhiteSpace($DatasourceUrl)) {
    $env:MALL_DATASOURCE_URL = $DatasourceUrl
}
if (-not [string]::IsNullOrWhiteSpace($DatasourceUsername)) {
    $env:MALL_DATASOURCE_USERNAME = $DatasourceUsername
}
if (-not [string]::IsNullOrWhiteSpace($DatasourcePassword)) {
    $env:MALL_DATASOURCE_PASSWORD = $DatasourcePassword
}

if ([string]::IsNullOrWhiteSpace($env:CIYUANSHEN_TEXT_API_KEY) -and
    [string]::IsNullOrWhiteSpace($env:CIYUANSHEN_API_KEY)) {
    throw '未找到词元神 Key。请设置 CIYUANSHEN_API_KEY 或 CIYUANSHEN_TEXT_API_KEY。'
}

Push-Location $backendRoot
try {
    # 先把 reactor 依赖安装到本地 Maven 仓库，避免 spring-boot:run 在父 POM 上寻找主类。
    mvn -pl mall-portal -am install '-DskipTests' '-Ddocker.skip=true'
    if ($LASTEXITCODE -ne 0) {
        throw "Maven 依赖构建失败，退出码：$LASTEXITCODE"
    }
    if ([string]::IsNullOrWhiteSpace($Profile)) {
        $Profile = if ($Mode -eq 'assistant') { 'assistant' } else { 'dev' }
    }

    $runArguments = @("-Dspring-boot.run.profiles=$Profile")
    if ($Mode -eq 'assistant') {
        $runArguments += '-Dstart-class=com.macro.mall.portal.assistant.AssistantApplication'
    }

    mvn -pl mall-portal @runArguments spring-boot:run
    if ($LASTEXITCODE -ne 0) {
        throw "商城后端启动失败，退出码：$LASTEXITCODE"
    }
}
finally {
    Pop-Location
}
