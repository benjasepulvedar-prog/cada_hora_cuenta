$ErrorActionPreference = 'Stop'

$tools = Join-Path $env:LOCALAPPDATA 'Programs\cada-hora-cuenta-tools'
$env:JAVA_HOME = Join-Path $tools 'jdk-17'
$maven = Join-Path $tools 'apache-maven-3.9.16\bin\mvn.cmd'
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + (Join-Path $tools 'apache-maven-3.9.16\bin') + ';' + $env:Path
$env:SPRING_PROFILES_ACTIVE = 'neon'

if ([string]::IsNullOrWhiteSpace($env:DB_URL)) {
    $env:DB_URL = Read-Host 'JDBC URL de Neon (jdbc:postgresql://...)'
}
if ([string]::IsNullOrWhiteSpace($env:DB_URL)) {
    throw 'DB_URL es obligatorio para el perfil neon.'
}

if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME) -or [string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
    $credential = Get-Credential -UserName 'neondb_owner' -Message 'Credenciales de Neon'
    if ($null -eq $credential) {
        throw 'Se requieren credenciales de Neon para iniciar el perfil.'
    }
    $env:DB_USERNAME = $credential.UserName
    $env:DB_PASSWORD = $credential.GetNetworkCredential().Password
}

if (-not (Test-Path -LiteralPath $maven)) {
    throw "No se encontró Maven en '$maven'. Instala Maven 3.9.16 o actualiza run-neon.ps1."
}

Set-Location $PSScriptRoot
& $maven spring-boot:run
exit $LASTEXITCODE
