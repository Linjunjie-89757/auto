$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$serverDir = Join-Path $repoRoot 'server'

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

if (-not $env:MAVEN_HOME) {
  $defaultMavenHome = 'C:\Program Files\Apache\apache-maven-3.9.15'
  if (Test-Path $defaultMavenHome) {
    $env:MAVEN_HOME = $defaultMavenHome
  }
}

if ($env:JAVA_HOME) {
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

if ($env:MAVEN_HOME) {
  $env:Path = "$env:MAVEN_HOME\bin;$env:Path"
}

Set-Location $serverDir
$mavenCommand = $null
$mavenArgs = @('spring-boot:run')

if (Test-Path (Join-Path $serverDir 'mvnw.cmd')) {
  $mavenCommand = '.\mvnw.cmd'
}
elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
  $mavenCommand = 'mvn'
}
else {
  throw 'Neither mvnw.cmd nor mvn is available. Please restore the Maven wrapper or install Maven before running Playwright smoke tests.'
}

if ($env:BACKEND_PROFILE) {
  $mavenArgs += "-Dspring-boot.run.profiles=$env:BACKEND_PROFILE"
}

if ($env:BACKEND_PORT) {
  $mavenArgs += "-Dspring-boot.run.arguments=--server.port=$env:BACKEND_PORT"
}

& $mavenCommand @mavenArgs
