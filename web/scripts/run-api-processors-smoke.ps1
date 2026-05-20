param(
  [int]$BackendPort = 18081,
  [int]$FrontendPort = 4176,
  [string[]]$Specs = @('tests/api-processors-smoke.spec.ts'),
  [switch]$KeepDatabase
)

$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$serverDir = Join-Path $repoRoot 'server'
$webDir = Join-Path $repoRoot 'web'
$migrationDir = Join-Path $serverDir 'src\main\resources\db\migration'
$smokeDataDir = Join-Path $serverDir 'data\smoke'

function Initialize-JavaPath {
  if (-not $env:JAVA_HOME) {
    $machineJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
    $userJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
    $candidateJavaHomes = @(
      $machineJavaHome
      $userJavaHome
      'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
    ) | Where-Object { $_ -and (Test-Path $_) }

    if ($candidateJavaHomes.Count -gt 0) {
      $env:JAVA_HOME = $candidateJavaHomes[0]
    }
  }

  if ($env:JAVA_HOME) {
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  }
}

function Get-H2Jar {
  $h2Root = Join-Path $env:USERPROFILE '.m2\repository\com\h2database\h2'
  $h2Jar = Get-ChildItem -Path $h2Root -Recurse -Filter 'h2-*.jar' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

  if (-not $h2Jar) {
    throw 'H2 jar was not found in the local Maven cache. Run server compile once before this smoke test.'
  }

  return $h2Jar.FullName
}

function Get-VersionedMigration {
  Get-ChildItem -Path $migrationDir -Filter 'V*.sql' |
    ForEach-Object {
      if ($_.Name -match '^V(\d+)__') {
        [PSCustomObject]@{
          Version = [int]$Matches[1]
          File = $_
        }
      }
    } |
    Sort-Object Version
}

function Remove-SmokeDatabase {
  param(
    [string]$DatabaseName
  )

  $resolvedSmokeDir = [System.IO.Path]::GetFullPath($smokeDataDir)
  $targets = @(
    (Join-Path $smokeDataDir "$DatabaseName.mv.db"),
    (Join-Path $smokeDataDir "$DatabaseName.trace.db")
  )

  foreach ($target in $targets) {
    if (-not (Test-Path -LiteralPath $target)) {
      continue
    }

    $resolvedTarget = [System.IO.Path]::GetFullPath($target)
    if (-not $resolvedTarget.StartsWith($resolvedSmokeDir, [System.StringComparison]::OrdinalIgnoreCase)) {
      throw "Refusing to remove path outside smoke data directory: $resolvedTarget"
    }

    Remove-Item -LiteralPath $resolvedTarget -Force
  }
}

Initialize-JavaPath
$javaCommand = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCommand) {
  throw 'Java is not available. Set JAVA_HOME or install JDK 21 before running this smoke test.'
}

$h2Jar = Get-H2Jar
New-Item -ItemType Directory -Path $smokeDataDir -Force | Out-Null

$databaseName = "api-processors-smoke-$([DateTime]::Now.ToString('yyyyMMddHHmmss'))"
$databaseUrl = "jdbc:h2:file:./data/smoke/$databaseName;MODE=MySQL;DATABASE_TO_LOWER=TRUE"

Write-Host "Preparing API processors smoke database: $databaseUrl"
Push-Location $serverDir
try {
  foreach ($migration in Get-VersionedMigration) {
    if ($migration.Version -eq 21) {
      Write-Host "Skipping V21 for smoke database: legacy empty-db duplicate execution_note migration"
      continue
    }

    Write-Host "Applying $($migration.File.Name)"
    & $javaCommand.Source -cp $h2Jar org.h2.tools.RunScript `
      -url $databaseUrl `
      -user sa `
      -script $migration.File.FullName

    if ($LASTEXITCODE -ne 0) {
      throw "Failed to apply migration $($migration.File.Name)"
    }
  }
}
finally {
  Pop-Location
}

$env:PLAYWRIGHT_BACKEND_PORT = "$BackendPort"
$env:PLAYWRIGHT_FRONTEND_PORT = "$FrontendPort"
$env:DB_URL = $databaseUrl
$env:SPRING_FLYWAY_ENABLED = 'false'
$env:CI = '1'

Write-Host "Running API processors smoke on backend port $BackendPort and frontend port $FrontendPort"
Set-Location $webDir
& powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-playwright.ps1 test @Specs --project=chromium --workers=1
$playwrightExitCode = $LASTEXITCODE

if ($playwrightExitCode -eq 0 -and -not $KeepDatabase) {
  Remove-SmokeDatabase -DatabaseName $databaseName
}
else {
  Write-Host "Smoke database kept at: $(Join-Path $smokeDataDir "$databaseName.mv.db")"
}

exit $playwrightExitCode
