$ErrorActionPreference = 'Stop'
$process = $null
try {
    $root = $PSScriptRoot
    $java = Join-Path $root 'runtime\bin\java.exe'
    if (-not (Test-Path $java)) { throw 'Runtime ausente. Extraia o ZIP inteiro antes de iniciar.' }
    $port = 18765
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $port)
    try { $listener.Start() } catch { throw "A porta $port esta ocupada. Encerre outra instancia do Revora e tente novamente." }
    finally { $listener.Stop() }
    $logs = Join-Path $env:LOCALAPPDATA 'Revora\logs'
    New-Item -ItemType Directory -Force -Path $logs | Out-Null
    $stamp = [Guid]::NewGuid().ToString('N')
    $outLog = Join-Path $logs "$stamp.out.log"
    $errLog = Join-Path $logs "$stamp.err.log"
    $process = Start-Process -FilePath $java -WorkingDirectory $root -PassThru -WindowStyle Hidden `
        -ArgumentList @('-jar', 'revora.jar', '--spring.profiles.active=local', "--server.port=$port", '--server.address=127.0.0.1') `
        -RedirectStandardOutput $outLog -RedirectStandardError $errLog
    $url = "http://127.0.0.1:$port"
    $ready = $false
    for ($i = 0; $i -lt 120; $i++) {
        if ($process.HasExited) { throw "O Revora encerrou durante a inicializacao. Consulte $logs" }
        try {
            $health = Invoke-RestMethod "$url/api/local/health" -TimeoutSec 2
            if ($health.service -eq 'revora-local') { $ready = $true; break }
        } catch { Start-Sleep -Milliseconds 500 }
    }
    if (-not $ready) { throw "Tempo de inicializacao excedido. Consulte $logs" }
    Start-Process "$url/local.html"
    Write-Host 'Revora aberto no navegador. Os arquivos sao processados neste computador.'
    Write-Host 'Mantenha esta janela aberta. Para encerrar, pressione Enter.'
    Read-Host | Out-Null
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
} finally {
    if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id -ErrorAction SilentlyContinue }
}
