param([Parameter(Mandatory=$true)][string]$PackageDirectory)
$ErrorActionPreference = 'Stop'
$process = $null
$logs = Join-Path ([IO.Path]::GetTempPath()) ('revora-smoke-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $logs | Out-Null
try {
    # Let the OS allocate a free port; obtain it from Spring's startup log.
    $process = Start-Process (Join-Path $PackageDirectory 'runtime\bin\java.exe') -PassThru -WindowStyle Hidden `
        -WorkingDirectory $PackageDirectory -ArgumentList @('-jar','revora.jar','--spring.profiles.active=local','--server.port=0') `
        -RedirectStandardOutput (Join-Path $logs 'out.log') -RedirectStandardError (Join-Path $logs 'err.log')
    $url = $null
    for ($i = 0; $i -lt 120; $i++) {
        if ($process.HasExited) { throw 'Falha ao iniciar o runtime empacotado.' }
        $content = Get-Content (Join-Path $logs 'out.log') -Raw -ErrorAction SilentlyContinue
        if ($content -match 'Tomcat started on port (\d+)') {
            $candidate = "http://127.0.0.1:$($Matches[1])"
            try {
                if ((Invoke-RestMethod "$candidate/api/local/health" -TimeoutSec 2).service -eq 'revora-local') { $url = $candidate; break }
            } catch { }
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $url) { throw 'Runtime sem resposta HTTP.' }
    $page = Invoke-WebRequest "$url/local.html" -UseBasicParsing
    if ($page.Content -notmatch 'chameleon.svg') { throw 'Interface ausente do pacote.' }
    Add-Type -AssemblyName System.Net.Http
    Add-Type -AssemblyName System.IO.Compression
    $buffer = [IO.MemoryStream]::new()
    $archive = [IO.Compression.ZipArchive]::new($buffer, [IO.Compression.ZipArchiveMode]::Create, $true)
    $entries = @{
        '[Content_Types].xml' = '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>'
        '_rels/.rels' = '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>'
        'word/document.xml' = '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Uma palavra zzzzzzinexistente para revisar.</w:t></w:r></w:p><w:sectPr/></w:body></w:document>'
    }
    foreach ($name in $entries.Keys) {
        $entry = $archive.CreateEntry($name)
        $writer = [IO.StreamWriter]::new($entry.Open(), [Text.UTF8Encoding]::new($false))
        $writer.Write($entries[$name]); $writer.Dispose()
    }
    $archive.Dispose()
    $fixture = $buffer.ToArray(); $buffer.Dispose()
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.UseProxy = $false
    $http = [Net.Http.HttpClient]::new($handler)
    $http.DefaultRequestHeaders.Add('X-Revora-Local', '1')
    $form = [Net.Http.MultipartFormDataContent]::new()
    try {
        $form.Add([Net.Http.ByteArrayContent]::new($fixture), 'template', 'template.docx')
        $form.Add([Net.Http.ByteArrayContent]::new($fixture), 'documento', 'documento.docx')
        $response = $http.PostAsync("$url/api/local/revisar", $form).GetAwaiter().GetResult()
        $response.EnsureSuccessStatusCode() | Out-Null
        $result = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
        $stream = [IO.MemoryStream]::new($result, $false)
        $docx = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Read)
        try {
            $reader = [IO.StreamReader]::new($docx.GetEntry('word/document.xml').Open())
            $xml = $reader.ReadToEnd(); $reader.Dispose()
            if ($xml -notmatch 'zzzzzzinexistente' -or $xml -notmatch 'w:highlight') {
                throw 'DOCX sem o texto ou as marcacoes esperadas.'
            }
        } finally { $docx.Dispose(); $stream.Dispose() }
        $response.Dispose()
    } finally { $form.Dispose(); $http.Dispose() }
    Write-Host 'Smoke test Windows: runtime, health, interface e revisao DOCX com Hunspell OK.'
} catch {
    Get-Content (Join-Path $logs 'out.log') -ErrorAction SilentlyContinue | Write-Host
    Get-Content (Join-Path $logs 'err.log') -ErrorAction SilentlyContinue | Write-Host
    throw
} finally {
    if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id }
    Remove-Item $logs -Recurse -Force -ErrorAction SilentlyContinue
}
