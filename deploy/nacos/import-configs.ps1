# ============================================================
# 一键把 deploy/nacos/config/*.yaml 发布到 Nacos 配置中心
#
# 为什么要有这个脚本：
#   配置如果只在 Nacos 控制台手点一遍，换机 / 重建环境就全丢了，也无法追溯改动。
#   把配置内容入库 + 用 OpenAPI 发布，才能"配置即代码、可复现"。
#
# 【踩过的坑（务必保留这段实现）】
#   PowerShell 5.1 的 `Invoke-WebRequest -Body <hashtable>` 会用"系统默认编码"（中文 Windows = GBK）
#   构造表单体，导致中文注释以 GBK 字节存进 Nacos；而 Java 客户端按 UTF-8 解码，
#   报 `YAMLException: MalformedInputException: Input length = 1`，配置静默失效（应用回退默认值，
#   日志里却写着 "[Nacos Config] Load config ... success"）。
#   因此这里手工用 [Uri]::EscapeDataString 构造纯 ASCII 的表单体，并在发布后"回读校验 UTF-8"。
#
# 用法（先确保 Nacos 已启动）：
#   powershell -NoProfile -ExecutionPolicy Bypass -File deploy\nacos\import-configs.ps1
# 可选参数：-NacosAddr / -Group / -Username / -Password（开启鉴权时用）
# ============================================================
param(
    [string]$NacosAddr = 'http://localhost:8848',
    [string]$Group = 'DEFAULT_GROUP',
    [string]$Username = '',
    [string]$Password = ''
)

$ErrorActionPreference = 'Continue'
$configDir = Join-Path $PSScriptRoot 'config'
$utf8Read = New-Object System.Text.UTF8Encoding($false)
$utf8Strict = New-Object System.Text.UTF8Encoding($false, $true)   # 非法字节直接抛异常，用于校验

if (-not (Test-Path $configDir)) {
    Write-Output "CONFIG_DIR_NOT_FOUND: $configDir"
    exit 1
}

$ok = 0
$fail = 0

foreach ($file in Get-ChildItem -Path $configDir -Filter *.yaml | Sort-Object Name) {
    $dataId = $file.Name
    $content = [System.IO.File]::ReadAllText($file.FullName, $utf8Read)

    $parts = @(
        "dataId=$([Uri]::EscapeDataString($dataId))",
        "group=$([Uri]::EscapeDataString($Group))",
        "type=yaml",
        "content=$([Uri]::EscapeDataString($content))"
    )
    if ($Username) { $parts += "username=$([Uri]::EscapeDataString($Username))" }
    if ($Password) { $parts += "password=$([Uri]::EscapeDataString($Password))" }

    # 关键：整个 payload 已是纯 ASCII（百分号转义），用 UTF-8 编码发送即可，不再受系统默认编码影响
    $payloadBytes = [System.Text.Encoding]::UTF8.GetBytes([string]::Join('&', $parts))

    try {
        $resp = Invoke-WebRequest -Uri "$NacosAddr/nacos/v1/cs/configs" -Method POST `
            -Body $payloadBytes -ContentType 'application/x-www-form-urlencoded; charset=UTF-8' `
            -UseBasicParsing -TimeoutSec 10

        if ($resp.Content -ne 'true') {
            Write-Output "[WARN] publish returned unexpected response for ${dataId}: $($resp.Content)"
            $fail++
            continue
        }

        # 回读校验：确认 Nacos 里存的字节是合法 UTF-8（防止编码静默损坏）
        $check = Invoke-WebRequest -Uri "$NacosAddr/nacos/v1/cs/configs?dataId=$([Uri]::EscapeDataString($dataId))&group=$([Uri]::EscapeDataString($Group))" `
            -UseBasicParsing -TimeoutSec 10
        $stream = $check.RawContentStream
        $stream.Position = 0
        $bytes = New-Object byte[] $stream.Length
        $stream.Read($bytes, 0, $bytes.Length) | Out-Null

        try {
            $decoded = $utf8Strict.GetString($bytes)
            $match = ($decoded.Trim() -eq $content.Trim())
            Write-Output "[OK]   published+verified(utf8): $dataId (group=$Group, $($content.Length) chars, contentMatch=$match)"
            $ok++
        } catch {
            Write-Output "[FAIL] $dataId stored content is NOT valid UTF-8 -> $($_.Exception.Message)"
            $fail++
        }
    } catch {
        Write-Output "[FAIL] $dataId -> $($_.Exception.Message)"
        $fail++
    }
}

Write-Output "RESULT published=$ok failed=$fail"
if ($fail -gt 0) { exit 1 }
