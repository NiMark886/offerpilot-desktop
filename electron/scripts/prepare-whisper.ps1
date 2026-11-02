param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$electronRoot = Split-Path $PSScriptRoot -Parent
$whisperDir = Join-Path $electronRoot "resources/whisper"
$modelDir = Join-Path $whisperDir "models"
$modelPath = Join-Path $modelDir "ggml-small-q5_1.bin"
$expectedModelHash = "AE85E4A935D7A567BD102FE55AFC16BB595BDB618E11B2FC7591BC08120411BB"

New-Item -ItemType Directory -Force -Path $whisperDir, $modelDir | Out-Null

function Download-File {
    param(
        [string[]]$Urls,
        [string]$Destination,
        [string]$Label
    )
    $temp = "$Destination.download"
    foreach ($url in $Urls) {
        try {
            Write-Host "Downloading $Label from $url"
            if ($url -like "https://api.github.com/*") {
                & curl.exe -L --fail --retry 3 --connect-timeout 20 --max-time 1800 `
                    -H "Accept: application/octet-stream" -o $temp $url
            } else {
                & curl.exe -L --fail --retry 3 --connect-timeout 20 --max-time 1800 -o $temp $url
            }
            if ($LASTEXITCODE -eq 0 -and (Test-Path $temp) -and (Get-Item $temp).Length -gt 0) {
                Move-Item $temp $Destination -Force
                return
            }
        } catch {
            Write-Warning "Download failed: $url"
        }
        if (Test-Path $temp) { Remove-Item $temp -Force -ErrorAction SilentlyContinue }
    }
    throw "Unable to download $Label"
}

$requiredBinaries = @(
    "whisper-cli.exe",
    "whisper.dll",
    "ggml.dll",
    "ggml-base.dll"
)
$cpuDlls = @(
    "ggml-cpu-alderlake.dll",
    "ggml-cpu-cannonlake.dll",
    "ggml-cpu-cascadelake.dll",
    "ggml-cpu-haswell.dll",
    "ggml-cpu-icelake.dll",
    "ggml-cpu-sandybridge.dll",
    "ggml-cpu-skylakex.dll",
    "ggml-cpu-sse42.dll",
    "ggml-cpu-x64.dll"
)
$missingBinary = $requiredBinaries | Where-Object { -not (Test-Path (Join-Path $whisperDir $_)) }
if ($Force -or $missingBinary) {
    $zipPath = Join-Path $env:TEMP "offerpilot-whisper-bin.zip"
    $extractDir = Join-Path $env:TEMP "offerpilot-whisper-bin"
    if (Test-Path $extractDir) { Remove-Item $extractDir -Recurse -Force }
    Download-File -Urls @(
        "https://gh-proxy.com/https://github.com/ggml-org/whisper.cpp/releases/download/b5454/whisper-bin-x64.zip",
        "https://ghproxy.net/https://github.com/ggml-org/whisper.cpp/releases/download/b5454/whisper-bin-x64.zip",
        "https://api.github.com/repos/ggml-org/whisper.cpp/releases/assets/616096933"
    ) -Destination $zipPath -Label "whisper.cpp runtime"
    Expand-Archive -Path $zipPath -DestinationPath $extractDir -Force
    $releaseDir = Join-Path $extractDir "Release"
    foreach ($name in ($requiredBinaries + $cpuDlls)) {
        $source = Join-Path $releaseDir $name
        if (-not (Test-Path $source)) {
            throw "Whisper runtime file is missing: $name"
        }
        Copy-Item $source (Join-Path $whisperDir $name) -Force
    }
    Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
    Remove-Item $extractDir -Recurse -Force -ErrorAction SilentlyContinue
}

if ($Force -or -not (Test-Path $modelPath)) {
    Download-File -Urls @(
        "https://hf-mirror.com/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin"
    ) -Destination $modelPath -Label "Whisper small q5_1 model"
}

$actualHash = (Get-FileHash $modelPath -Algorithm SHA256).Hash
if ($actualHash -ne $expectedModelHash) {
    Remove-Item $modelPath -Force
    throw "Whisper model checksum mismatch. Expected $expectedModelHash, got $actualHash"
}

$notice = @"
whisper.cpp runtime: b5454 (Windows x64, CPU)
whisper.cpp: https://github.com/ggml-org/whisper.cpp (MIT)
Model: ggml-small-q5_1.bin (Whisper small, quantized)
Model source: https://huggingface.co/ggerganov/whisper.cpp
Whisper model weights: MIT
"@
Set-Content -Path (Join-Path $whisperDir "NOTICE.txt") -Value $notice -Encoding UTF8

Write-Host "Whisper runtime ready: $whisperDir"
Write-Host "Whisper model ready: $modelPath ($((Get-Item $modelPath).Length) bytes)"
