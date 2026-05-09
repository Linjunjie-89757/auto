$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$serverDir = Join-Path $repoRoot 'server'

if (-not $env:JAVA_HOME) {
  $defaultJavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
  if (Test-Path $defaultJavaHome) {
    $env:JAVA_HOME = $defaultJavaHome
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

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
  throw 'mvn is not available. Please install Maven or set MAVEN_HOME before running Playwright smoke tests.'
}

Set-Location $serverDir
$mavenArgs = @('spring-boot:run')

if ($env:BACKEND_PROFILE) {
  $mavenArgs += "-Dspring-boot.run.profiles=$env:BACKEND_PROFILE"
}

& mvn @mavenArgs
