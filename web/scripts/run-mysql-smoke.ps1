param(
  [string]$Suite = 'smoke'
)

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$webDir = Join-Path $repoRoot 'web'

if (-not $env:BACKEND_PROFILE) {
  $env:BACKEND_PROFILE = 'local-mysql'
}

if (-not $env:DB_URL) {
  $env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/auto_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
}

if (-not $env:DB_USERNAME) {
  $env:DB_USERNAME = 'auto_user'
}

if (-not $env:DB_PASSWORD) {
  $env:DB_PASSWORD = 'auto123456'
}

$env:CI = '1'

Set-Location $webDir
& npm.cmd run $Suite
