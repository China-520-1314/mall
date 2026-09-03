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

# 优先使用显式配置的文字 Key；没有时，仅为本次子进程读取 Codex 本地认证。
if ([string]::IsNullOrWhiteSpace($env:CIYUANSHEN_TEXT_API_KEY) -and
    (Test-Path -LiteralPath $authPath)) {
    $auth = Get-Content -LiteralPath $authPath -Raw | ConvertFrom-Json
    if (-not [string]::IsNullOrWhiteSpace($auth.OPENAI_API_KEY)) {
        $env:CIYUANSHEN_TEXT_API_KEY = [string]$auth.OPENAI_API_KEY
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

# 最后才兼容通用变量，避免意外使用其他模型供应商的 OPENAI_API_KEY。
if ([string]::IsNullOrWhiteSpace($env:CIYUANSHEN_TEXT_API_KEY) -and
    [string]::IsNullOrWhiteSpace($env:CIYUANSHEN_API_KEY) -and
    -not [string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY)) {
    $env:CIYUANSHEN_TEXT_API_KEY = [string]$env:OPENAI_API_KEY
}

if ([string]::IsNullOrWhiteSpace($env:CIYUANSHEN_TEXT_API_KEY) -and
    [string]::IsNullOrWhiteSpace($env:CIYUANSHEN_API_KEY)) {
    throw '未找到词元神 Key。请设置 CIYUANSHEN_TEXT_API_KEY，或确保当前用户存在 .codex/auth.json。'
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
