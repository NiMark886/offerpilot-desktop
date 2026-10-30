param([switch]$Volumes)

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if ($Volumes) {
    docker compose down -v
} else {
    docker compose down
}
