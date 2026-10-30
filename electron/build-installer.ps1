param(
    [switch]$ForceRuntime
)

$ErrorActionPreference = "Stop"
$appRoot = Split-Path $PSScriptRoot -Parent
$env:ELECTRON_MIRROR = "https://npmmirror.com/mirrors/electron/"
$env:ELECTRON_BUILDER_BINARIES_MIRROR = "https://npmmirror.com/mirrors/electron-builder-binaries/"

Write-Host "Building frontend..."
Push-Location (Join-Path $appRoot "frontend")
try {
    if (-not (Test-Path "node_modules")) { npm install }
    $env:OFFERPILOT_DESKTOP = "true"
    npm run build
    Remove-Item Env:OFFERPILOT_DESKTOP -ErrorAction SilentlyContinue
} finally {
    Pop-Location
}

Write-Host "Building backend jar..."
Push-Location (Join-Path $appRoot "backend")
try {
    mvn -DskipTests package
} finally {
    Pop-Location
}

Copy-Item (Join-Path $appRoot "backend/target/offerpilot-backend-0.1.0-SNAPSHOT.jar") `
    (Join-Path $PSScriptRoot "resources/backend.jar") -Force

$runtime = Join-Path $PSScriptRoot "resources/runtime"
if (-not (Test-Path (Join-Path $runtime "bin/java.exe")) -or $ForceRuntime) {
    Write-Host "Generating bundled Java runtime..."
    if (Test-Path $runtime) { Remove-Item $runtime -Recurse -Force }
    $jlink = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin/jlink.exe" } else { "jlink" }
    & $jlink `
        --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,jdk.crypto.ec,jdk.management,jdk.unsupported,jdk.zipfs `
        --strip-debug --no-header-files --no-man-pages --compress=2 `
        --output $runtime
}
if (Test-Path (Join-Path $PSScriptRoot "ui")) {
    Remove-Item (Join-Path $PSScriptRoot "ui") -Recurse -Force
}
Copy-Item (Join-Path $appRoot "frontend/dist") (Join-Path $PSScriptRoot "ui") -Recurse

Push-Location $PSScriptRoot
try {
    if (-not (Test-Path "node_modules")) { npm install }
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "Electron build failed" }
} finally {
    Pop-Location
}

$installer = Get-ChildItem (Join-Path $PSScriptRoot "dist") -Filter "*.exe" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $installer) { throw "Electron installer was not generated" }
Copy-Item $installer.FullName (Join-Path $appRoot "OfferPilot-Electron-0.1.0-x64-setup.exe") -Force
Write-Host "Installer ready: $(Join-Path $appRoot 'OfferPilot-Electron-0.1.0-x64-setup.exe')"
