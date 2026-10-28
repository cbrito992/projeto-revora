param(
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$OutputDirectory = '',
    [string]$Version = '0.2.0'
)
$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'Gere este pacote no Windows com JDK 21 x64.' }
if ($Version -notmatch '^\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?$') { throw 'Versao invalida.' }
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $repo 'dist' }
if (-not $JdkHome -or -not (Test-Path (Join-Path $JdkHome 'bin\jlink.exe'))) { throw 'Informe -JdkHome com um JDK 21 completo para Windows x64.' }
$release = Get-Content (Join-Path $JdkHome 'release') -Raw
if ($release -notmatch 'JAVA_VERSION="21[.\"]' -or $release -notmatch 'OS_ARCH="(?:amd64|x86_64)"') { throw 'Use JDK 21 x64 para Windows.' }
$env:JAVA_HOME = $JdkHome
$env:Path = (Join-Path $JdkHome 'bin') + ';' + $env:Path
Push-Location (Join-Path $repo 'app')
try {
    & .\mvnw.cmd -B -ntp clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Build ou testes falharam; nenhum pacote foi gerado.' }
} finally { Pop-Location }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$OutputDirectory = (Resolve-Path $OutputDirectory).Path
$stage = Join-Path $OutputDirectory ('stage-' + [Guid]::NewGuid().ToString('N'))
$package = Join-Path $stage 'Revora'
New-Item -ItemType Directory -Path $package -Force | Out-Null
try {
    & (Join-Path $JdkHome 'bin\jlink.exe') --add-modules java.se,jdk.crypto.ec,jdk.unsupported,jdk.naming.dns,jdk.zipfs,jdk.charsets `
        --strip-debug --no-header-files --no-man-pages --output (Join-Path $package 'runtime')
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar o runtime Java.' }
    Copy-Item (Join-Path $repo 'app\target\revora-0.0.1-SNAPSHOT.jar') (Join-Path $package 'revora.jar')
    Copy-Item (Join-Path $PSScriptRoot 'Iniciar-Revora.cmd') $package
    Copy-Item (Join-Path $PSScriptRoot 'Iniciar-Revora.ps1') $package
    Copy-Item (Join-Path $repo 'docs\WINDOWS.md') (Join-Path $package 'LEIA-ME.md')
    Copy-Item (Join-Path $repo 'LICENSE') $package
    # Preserve the JDK distributor's notices in addition to jlink's module legal files.
    foreach ($notice in @('NOTICE', 'LICENSE')) {
        if (Test-Path (Join-Path $JdkHome $notice)) { Copy-Item (Join-Path $JdkHome $notice) (Join-Path $package "JAVA-$notice") }
    }
    & (Join-Path $PSScriptRoot 'Test-Package.ps1') -PackageDirectory $package
    $zip = Join-Path $OutputDirectory "Revora-$Version-windows-x64.zip"
    if (Test-Path $zip) { throw "O pacote ja existe: $zip. Escolha outra versao ou diretorio." }
    Compress-Archive -Path $package -DestinationPath $zip
    $hash = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $([System.IO.Path]::GetFileName($zip))" | Set-Content "$zip.sha256" -Encoding ascii
    Write-Host "Pacote: $zip"
    Write-Host "SHA256: $hash"
} finally { if (Test-Path $stage) { Remove-Item $stage -Recurse -Force } }
