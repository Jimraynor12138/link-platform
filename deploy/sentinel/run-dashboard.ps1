# Run Sentinel Dashboard on port 8858 (login: sentinel / sentinel, http://localhost:8858).
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File deploy\sentinel\run-dashboard.ps1
$jar = Join-Path $PSScriptRoot 'sentinel-dashboard-1.8.8.jar'
if (-not (Test-Path $jar)) {
    Write-Output 'MISSING_JAR: please download sentinel-dashboard-1.8.8.jar from GitHub releases into this dir (see README.md)'
    exit 1
}
java -Dserver.port=8858 -Dcsp.sentinel.dashboard.server=localhost:8858 -jar $jar
