param(
  [int]$IntervalSeconds = 30,
  [string]$ScriptName = "collector-proxmox.ps1"
)

$ErrorActionPreference = 'Stop'

function Write-Info($m){ $ts=(Get-Date).ToString('yyyy-MM-dd HH:mm:ss'); Write-Host "[$ts] $m" }

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$target = Join-Path $here $ScriptName
if (-not (Test-Path $target)) { throw "Script not found: $target" }

Write-Info "Loop starting. Every $IntervalSeconds s: $target"
while ($true) {
  try {
    & $target
  } catch {
    Write-Info "Error: $($_.Exception.Message)"
  }
  Start-Sleep -Seconds $IntervalSeconds
}

