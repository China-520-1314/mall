$ErrorActionPreference = 'Stop'
$catalogRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$catalog = Get-Content -LiteralPath (Join-Path $catalogRoot 'document/catalog/mi-20260911.json') -Raw -Encoding utf8 | ConvertFrom-Json
$downloadDir = Join-Path $catalogRoot 'output/catalog-images'
New-Item -ItemType Directory -Path $downloadDir -Force | Out-Null
$results = $catalog.products | ForEach-Object -Parallel {
    $ErrorActionPreference = 'Continue'
    $target = Join-Path $using:downloadDir ($_.source_id + '.source')
    $uri = [uri]$_.source_image_url
    if ($uri.Scheme -ne 'https' -or $uri.Host -notin @('cdn.cnbj1.fds.api.mi-img.com','cdn.cnbj0.fds.api.mi-img.com')) { throw 'Unexpected image host' }
    if ((Test-Path -LiteralPath $target) -and (Get-Item -LiteralPath $target).Length -gt 1000) {
        [pscustomobject]@{ Id=$_.source_id; Success=$true; Name=$_.name }
        return
    }
    $downloadOutput = & curl.exe --fail --silent --show-error --location --ssl-revoke-best-effort --max-time 25 --retry 2 --retry-all-errors --output $target $uri.AbsoluteUri 2>&1
    [pscustomobject]@{ Id=$_.source_id; Success=($LASTEXITCODE -eq 0); Name=$_.name }
} -ThrottleLimit 4
$failed = @($results | Where-Object { -not $_.Success })
if ($failed.Count) { $failed | Format-Table; throw "$($failed.Count) images could not be downloaded" }
Write-Output "Downloaded $($results.Count) official product images."
