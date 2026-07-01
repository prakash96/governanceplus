param(
    [string]$ModelFile = ".\models\Llama-3.2-3B-Instruct-Q4_K_M.gguf",
    [int]$ChunkMB = 95
)

$chunkSize = $ChunkMB * 1024 * 1024
$outDir    = ".\models\chunks"
$baseName  = [System.IO.Path]::GetFileName($ModelFile)

New-Item -ItemType Directory -Force $outDir | Out-Null

$stream  = [System.IO.File]::OpenRead((Resolve-Path $ModelFile))
$buffer  = New-Object byte[] $chunkSize
$part    = 0

try {
    while (($bytesRead = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
        $chunkPath = Join-Path $outDir ("{0}.part{1:D3}" -f $baseName, $part)
        $zipPath   = "$chunkPath.zip"

        [System.IO.File]::WriteAllBytes($chunkPath, $buffer[0..($bytesRead - 1)])
        Compress-Archive -Path $chunkPath -DestinationPath $zipPath -Force
        Remove-Item $chunkPath

        Write-Host ("Part {0:D3} -> {1} ({2:N1} MB)" -f $part, (Split-Path $zipPath -Leaf), ((Get-Item $zipPath).Length / 1MB))
        $part++
    }
} finally {
    $stream.Close()
}

Write-Host "`nDone. $part chunks written to $outDir"
Write-Host "Reassemble on Linux with:"
Write-Host "  for f in models/chunks/*.zip; do unzip -p `"`$f`" >> model.gguf; done"
