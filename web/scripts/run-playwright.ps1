$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$browserPath = Join-Path $repoRoot '.playwright-browsers'

if (-not (Test-Path $browserPath)) {
  New-Item -ItemType Directory -Path $browserPath | Out-Null
}

$env:PLAYWRIGHT_BROWSERS_PATH = $browserPath

Set-Location $repoRoot
& .\node_modules\.bin\playwright.cmd @args
exit $LASTEXITCODE
