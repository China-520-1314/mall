param(
    [ValidateSet('portal', 'assistant')]
    [string]$Mode = 'portal',
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backend = Join-Path $root 'mall-backend'
$appWeb = Join-Path $root 'mall-app-web'
$adminWeb = Join-Path $root 'mall-admin-web'
$portalJar = Join-Path $backend 'mall-portal\target\mall-portal-1.0-SNAPSHOT.jar'
$adminJar = Join-Path $backend 'mall-admin\target\mall-admin-1.0-SNAPSHOT.jar'
$portalResources = Join-Path $backend 'mall-portal\src\main\resources'
$adminResources = Join-Path $backend 'mall-admin\src\main\resources'
$portalRunner = Join-Path $backend 'run-mall-portal.ps1'
$localEnvironmentFile = Join-Path $root 'mall.local.env'
$ports = @(5173, 5174, 8080, 8085)

function Assert-Path([string]$path, [string]$description) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing $description`: $path"
    }
}

function Resolve-Executable([string]$name) {
    $command = Get-Command $name -ErrorAction Stop
    if (-not [string]::IsNullOrWhiteSpace($command.Source)) {
        return $command.Source
    }
    if (-not [string]::IsNullOrWhiteSpace($command.Path)) {
        return $command.Path
    }
    throw "Unable to resolve executable: $name"
}

function ConvertTo-PowerShellLiteral([string]$value) {
    return "'" + $value.Replace("'", "''") + "'"
}

function ConvertTo-FileUri([string]$path) {
    $resolvedPath = (Resolve-Path -LiteralPath $path).Path
    return ([System.Uri]::new($resolvedPath)).AbsoluteUri.TrimEnd('/') + '/'
}

function Initialize-CiyuanshenKey {
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

    $authPath = Join-Path $env:USERPROFILE '.codex\auth.json'
    if (-not (Test-Path -LiteralPath $authPath)) {
        Write-Warning 'Ciyuanshen key is not configured. The customer assistant will use its fallback response.'
        return
    }

    try {
        $auth = Get-Content -LiteralPath $authPath -Raw | ConvertFrom-Json
        $textKeyProperty = $auth.PSObject.Properties['CIYUANSHEN_TEXT_API_KEY']
        $apiKeyProperty = $auth.PSObject.Properties['CIYUANSHEN_API_KEY']
        if ($null -ne $textKeyProperty -and -not [string]::IsNullOrWhiteSpace($textKeyProperty.Value)) {
            $env:CIYUANSHEN_TEXT_API_KEY = [string]$textKeyProperty.Value
        } elseif ($null -ne $apiKeyProperty -and -not [string]::IsNullOrWhiteSpace($apiKeyProperty.Value)) {
            $env:CIYUANSHEN_API_KEY = [string]$apiKeyProperty.Value
        } else {
            Write-Warning 'Ciyuanshen key is not configured. Set CIYUANSHEN_API_KEY or CIYUANSHEN_TEXT_API_KEY.'
        }
    } catch {
        Write-Warning 'Unable to load a Ciyuanshen key from Codex auth.json. Set CIYUANSHEN_API_KEY or CIYUANSHEN_TEXT_API_KEY.'
    }
}

function Initialize-DatasourceEnvironment {
    if ([string]::IsNullOrWhiteSpace($env:MALL_DATASOURCE_URL) -and
        -not [string]::IsNullOrWhiteSpace($env:SPRING_DATASOURCE_URL)) {
        $env:MALL_DATASOURCE_URL = [string]$env:SPRING_DATASOURCE_URL
    }
    if ([string]::IsNullOrWhiteSpace($env:MALL_DATASOURCE_USERNAME) -and
        -not [string]::IsNullOrWhiteSpace($env:SPRING_DATASOURCE_USERNAME)) {
        $env:MALL_DATASOURCE_USERNAME = [string]$env:SPRING_DATASOURCE_USERNAME
    }
    if ([string]::IsNullOrWhiteSpace($env:MALL_DATASOURCE_PASSWORD) -and
        -not [string]::IsNullOrWhiteSpace($env:SPRING_DATASOURCE_PASSWORD)) {
        $env:MALL_DATASOURCE_PASSWORD = [string]$env:SPRING_DATASOURCE_PASSWORD
    }
}

function Initialize-LocalEnvironment {
    if (-not (Test-Path -LiteralPath $localEnvironmentFile)) {
        return
    }

    $allowedNames = @(
        'MALL_DATASOURCE_URL',
        'MALL_DATASOURCE_USERNAME',
        'MALL_DATASOURCE_PASSWORD'
    )

    foreach ($line in Get-Content -LiteralPath $localEnvironmentFile -Encoding UTF8) {
        $trimmedLine = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmedLine) -or $trimmedLine.StartsWith('#')) {
            continue
        }

        if ($trimmedLine -notmatch '^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            Write-Warning ("Ignored invalid line in {0}." -f $localEnvironmentFile)
            continue
        }

        $name = $matches[1]
        if ($allowedNames -notcontains $name) {
            Write-Warning ("Ignored unsupported setting {0} in {1}." -f $name, $localEnvironmentFile)
            continue
        }

        $value = $matches[2].Trim()
        if ($value.Length -ge 2) {
            $first = $value.Substring(0, 1)
            $last = $value.Substring($value.Length - 1, 1)
            if (($first -eq '"' -and $last -eq '"') -or ($first -eq "'" -and $last -eq "'")) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }

        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
}

function Get-ListeningConnections([int[]]$targetPorts) {
    return @(
        Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
            Where-Object { $targetPorts -contains [int]$_.LocalPort }
    )
}

function Start-LocalRedis {
    if (@(Get-ListeningConnections @(6379)).Count -gt 0) {
        Write-Host 'Redis port 6379 is already listening; reusing the existing service.'
        return
    }
    $redisDirectory = Join-Path $root 'runtime\redis'
    $redisExecutable = Join-Path $redisDirectory 'redis-server.exe'
    $redisConfiguration = Join-Path $redisDirectory 'redis.windows.conf'
    Assert-Path $redisExecutable 'local Redis runtime'
    Assert-Path $redisConfiguration 'local Redis configuration'
    $logDirectory = Join-Path $root 'runtime\launcher-logs'
    $null = New-Item -ItemType Directory -Path $logDirectory -Force
    Start-Process -FilePath $redisExecutable -WorkingDirectory $redisDirectory -ArgumentList @('"' + $redisConfiguration + '"') `
        -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDirectory 'redis.out.log') `
        -RedirectStandardError (Join-Path $logDirectory 'redis.err.log') | Out-Null
    Write-Host 'Started local Redis on 127.0.0.1:6379.'
}

function Initialize-Database {
    if ($env:MALL_DATASOURCE_URL -notmatch '^jdbc:mysql://(?<host>\[[^\]]+\]|[^/:?]+)(:(?<port>\d+))?/') {
        throw 'Expected a jdbc:mysql://host:port/database URL in mall.local.env.'
    }
    $databaseHost = $matches['host'].Trim('[', ']')
    $databasePort = 3306
    if ($matches['port']) { $databasePort = [int]$matches['port'] }
    if ($databaseHost -in @('localhost', '127.0.0.1', '::1') -and
        @(Get-ListeningConnections @($databasePort)).Count -eq 0) {
        $services = @(Get-Service -Name '*mysql*' -ErrorAction SilentlyContinue)
        if ($services.Count -ne 1) {
            throw 'MySQL is not listening. Start the intended MySQL instance, then retry.'
        }
        if ($services[0].Status -ne 'Running') {
            try {
                Start-Service -InputObject $services[0] -ErrorAction Stop
                $services[0].WaitForStatus('Running', [TimeSpan]::FromSeconds(20))
            } catch {
                throw 'Unable to start MySQL. Start its Windows service with administrator rights, then retry.'
            }
        }
    }
    # 从后端包提取同版本驱动，不依赖本机 mysql 命令或硬编码 Maven 缓存路径。
    Assert-Path $portalJar 'portal package (build the backend first)'
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($portalJar)
    try {
        $entry = $archive.Entries | Where-Object { $_.FullName -match '^BOOT-INF/lib/mysql-connector-j-[^/]+\.jar$' } | Select-Object -First 1
        if ($null -eq $entry) { throw 'MySQL JDBC driver is missing from the portal package.' }
        $directory = Join-Path $root 'runtime\database-check'
        $null = New-Item -ItemType Directory -Path $directory -Force
        $driver = Join-Path $directory $entry.Name
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $driver, $true)
    } finally { $archive.Dispose() }
    & $javaPath --class-path $driver (Join-Path $root 'scripts\CheckDatabase.java')
    if ($LASTEXITCODE -ne 0) { throw 'Database verification failed; existing application processes were left running.' }
}

function Wait-ProjectReadiness {
    $pending = @{
        'App frontend' = 'http://localhost:5173/'
        'Admin frontend' = 'http://localhost:5174/'
        'Portal readiness' = 'http://localhost:8085/actuator/health/readiness'
        'Admin readiness' = 'http://localhost:8080/actuator/health/readiness'
    }
    $deadline = (Get-Date).AddSeconds(60)
    while ($pending.Count -gt 0 -and (Get-Date) -lt $deadline) {
        foreach ($serviceName in @($pending.Keys)) {
            try {
                $result = Invoke-WebRequest -Uri $pending[$serviceName] -UseBasicParsing -TimeoutSec 2
                if ($result.StatusCode -eq 200) {
                    Write-Host ("Ready: {0}" -f $serviceName) -ForegroundColor Green
                    $pending.Remove($serviceName)
                }
            } catch { }
        }
        if ($pending.Count -gt 0) { Start-Sleep -Milliseconds 500 }
    }
    if ($pending.Count -gt 0) {
        throw ("Services are not ready: {0}. See runtime\launcher-logs. No automatic assistant fallback was performed." -f ($pending.Keys -join ', '))
    }
}

function Stop-ProjectPortListeners([int[]]$targetPorts) {
    $connections = @(Get-ListeningConnections $targetPorts)
    $processIds = $connections | Select-Object -ExpandProperty OwningProcess -Unique

    # 在停止任何进程前核对归属，不能仅凭端口号终止其他项目。
    foreach ($processId in $processIds) {
        $details = Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
        if (-not $details.CommandLine -or $details.CommandLine.IndexOf($root, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
            throw ("Port is occupied by PID {0} outside this project. Close that application or change its port." -f $processId)
        }
    }

    foreach ($processId in $processIds) {
        if (-not $processId -or $processId -eq $PID) {
            continue
        }

        try {
            $process = Get-Process -Id $processId -ErrorAction Stop
            Stop-Process -Id $processId -Force -ErrorAction Stop
            Write-Host ("Stopped PID {0} ({1}) on a project port." -f $processId, $process.ProcessName) -ForegroundColor Yellow
        } catch {
            Write-Warning ("Could not stop PID {0}: {1}" -f $processId, $_.Exception.Message)
        }
    }

    $deadline = (Get-Date).AddSeconds(5)
    do {
        $remaining = @(Get-ListeningConnections $targetPorts)
        if ($remaining.Count -eq 0) {
            return
        }
        Start-Sleep -Milliseconds 200
    } while ((Get-Date) -lt $deadline)

    $blockedPorts = $remaining | Select-Object -ExpandProperty LocalPort -Unique | Sort-Object
    throw ("The following project ports are still occupied: {0}" -f ($blockedPorts -join ', '))
}

function Start-ProjectWindow([string]$workingDirectory, [string]$command) {
    $logDirectory = Join-Path $root 'runtime\launcher-logs'
    $null = New-Item -ItemType Directory -Path $logDirectory -Force
    $script:launchIndex++
    $logPrefix = Join-Path $logDirectory ("service-{0}" -f $script:launchIndex)
    $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
    Start-Process -FilePath $shellPath -WorkingDirectory $workingDirectory -ArgumentList @(
        '-NoLogo', '-NoProfile', '-ExecutionPolicy', 'Bypass',
        '-EncodedCommand', $encodedCommand
    ) -WindowStyle Hidden -RedirectStandardOutput ($logPrefix + '.out.log') -RedirectStandardError ($logPrefix + '.err.log') | Out-Null
}

Assert-Path (Join-Path $backend 'pom.xml') 'backend project'
Assert-Path (Join-Path $appWeb 'package.json') 'app frontend project'
Assert-Path (Join-Path $adminWeb 'package.json') 'admin frontend project'
Assert-Path $portalResources 'portal resources'
Assert-Path $adminResources 'admin resources'

$shellPath = Get-Command pwsh.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1
if ([string]::IsNullOrWhiteSpace($shellPath)) {
    $shellPath = Resolve-Executable 'powershell.exe'
}

$javaPath = Resolve-Executable 'java.exe'
$npmPath = Resolve-Executable 'npm.cmd'
$requiresMaven = -not (Test-Path -LiteralPath $portalJar) -or -not (Test-Path -LiteralPath $adminJar)
if ($requiresMaven) {
    $null = Resolve-Executable 'mvn.cmd'
    Assert-Path $portalRunner 'portal fallback runner'
}

$portalConfigArgument = "--spring.config.location=$(ConvertTo-FileUri $portalResources)"
$adminConfigArgument = "--spring.config.location=$(ConvertTo-FileUri $adminResources)"
$javaLiteral = ConvertTo-PowerShellLiteral $javaPath
$npmLiteral = ConvertTo-PowerShellLiteral $npmPath
$portalJarLiteral = ConvertTo-PowerShellLiteral $portalJar
$adminJarLiteral = ConvertTo-PowerShellLiteral $adminJar
$portalConfigLiteral = ConvertTo-PowerShellLiteral $portalConfigArgument
$adminConfigLiteral = ConvertTo-PowerShellLiteral $adminConfigArgument
$portalRunnerLiteral = ConvertTo-PowerShellLiteral $portalRunner
$shellLiteral = ConvertTo-PowerShellLiteral $shellPath

Write-Host '[1/5] Checking projects and local runtimes...' -ForegroundColor Cyan
Initialize-LocalEnvironment
Initialize-CiyuanshenKey
Initialize-DatasourceEnvironment

$datasourceConfigured =
    (-not [string]::IsNullOrWhiteSpace($env:MALL_DATASOURCE_URL) -and
     -not [string]::IsNullOrWhiteSpace($env:MALL_DATASOURCE_USERNAME) -and
     -not [string]::IsNullOrWhiteSpace($env:MALL_DATASOURCE_PASSWORD))

if ($Mode -eq 'portal' -and -not $datasourceConfigured) {
    throw 'Full mall mode requires MySQL settings in mall.local.env. For FAQ-only mode, explicitly use -Mode assistant.'
}
if ($Mode -eq 'portal') {
    if (-not (Test-Path -LiteralPath $portalJar)) {
        & mvn.cmd -f (Join-Path $backend 'pom.xml') -pl mall-portal -am -DskipTests -Ddocker.skip=true package
        if ($LASTEXITCODE -ne 0) { throw 'Backend build failed before database verification.' }
    }
    Initialize-Database
}
if ($CheckOnly) {
    Write-Host 'Configuration and database checks passed; application processes were not restarted.' -ForegroundColor Green
    return
}
Start-LocalRedis

Write-Host '[2/5] Freeing project ports: 5173, 5174, 8080, 8085...' -ForegroundColor Cyan
Stop-ProjectPortListeners $ports

Write-Host '[3/5] Starting portal backend (8085)...' -ForegroundColor Cyan
if ($Mode -eq 'assistant') {
    Write-Host 'Explicit assistant-only mode selected; product and order APIs are unavailable.' -ForegroundColor DarkYellow
    Start-ProjectWindow $backend @"
`$Host.UI.RawUI.WindowTitle = 'Mall Assistant Backend - 8085'
& $shellLiteral -NoProfile -ExecutionPolicy Bypass -File $portalRunnerLiteral -Mode assistant
if (`$LASTEXITCODE -ne 0) {
    Write-Host ("Assistant backend stopped with exit code {0}." -f `$LASTEXITCODE) -ForegroundColor Red
}
"@
} elseif (Test-Path -LiteralPath $portalJar) {
    Start-ProjectWindow $backend @"
`$Host.UI.RawUI.WindowTitle = 'Mall Portal Backend - 8085'
& $javaLiteral @('-jar', $portalJarLiteral, '--server.port=8085', $portalConfigLiteral)
if (`$LASTEXITCODE -ne 0) {
    Write-Error ("Portal backend stopped with exit code {0}. Check the database and service logs." -f `$LASTEXITCODE)
}
"@
} else {
    Start-ProjectWindow $backend @"
`$Host.UI.RawUI.WindowTitle = 'Mall Portal Backend - 8085'
& $shellLiteral -NoProfile -ExecutionPolicy Bypass -File $portalRunnerLiteral -Mode portal
if (`$LASTEXITCODE -ne 0) {
    Write-Error ("Portal backend stopped with exit code {0}. Check the database and service logs." -f `$LASTEXITCODE)
}
"@
}

Write-Host '[4/5] Starting admin backend (8080)...' -ForegroundColor Cyan
if (Test-Path -LiteralPath $adminJar) {
    Start-ProjectWindow $backend @"
`$Host.UI.RawUI.WindowTitle = 'Mall Admin Backend - 8080'
& $javaLiteral @('-jar', $adminJarLiteral, '--server.port=8080', $adminConfigLiteral)
if (`$LASTEXITCODE -ne 0) {
    Write-Host ("Admin backend stopped with exit code {0}." -f `$LASTEXITCODE) -ForegroundColor Red
}
"@
} else {
    Start-ProjectWindow $backend @"
`$Host.UI.RawUI.WindowTitle = 'Mall Admin Backend - 8080'
`$installArgs = @('-pl', 'mall-admin', '-am', '-DskipTests', '-Ddocker.skip=true', 'install')
& mvn @installArgs
if (`$LASTEXITCODE -eq 0) {
    `$runArgs = @('-pl', 'mall-admin', '-Dspring-boot.run.profiles=dev', '-Dserver.port=8080', 'spring-boot:run')
    & mvn @runArgs
}
"@
}

Write-Host '[5/5] Starting app and admin frontends...' -ForegroundColor Cyan
Start-ProjectWindow $appWeb @"
`$Host.UI.RawUI.WindowTitle = 'Mall App Web - 5173'
& $npmLiteral run dev:h5 -- --host localhost --port 5173
"@
Start-ProjectWindow $adminWeb @"
`$Host.UI.RawUI.WindowTitle = 'Mall Admin Web - 5174'
& $npmLiteral run dev -- --host localhost --port 5174
"@

Write-Host ''
Wait-ProjectReadiness
Write-Host 'Services started in the background. Logs: runtime\launcher-logs' -ForegroundColor Green
Write-Host ('Portal mode: ' + $Mode)
Write-Host 'App frontend:   http://localhost:5173/'
Write-Host 'Admin frontend: http://localhost:5174/'
Write-Host 'Portal backend: http://localhost:8085/'
Write-Host 'Admin backend:  http://localhost:8080/'
Write-Host ''
Write-Host 'Only the four project ports are cleared; MySQL, Redis, MongoDB and RabbitMQ are not stopped.' -ForegroundColor DarkYellow
