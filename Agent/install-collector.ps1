param(
  [string]$InstallDir = "C:\OpsBoard\Agent",
  [string]$TaskName = "OpsBoardCollector",
  [string]$ScriptName = "collector-proxmox.ps1",
  [int]$IntervalSeconds = 30,
  [switch]$RunAsSystem = $true
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $InstallDir)) { New-Item -Force -ItemType Directory -Path $InstallDir | Out-Null }

# Copy required scripts
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
Copy-Item -Force -Path (Join-Path $here 'collector-proxmox.ps1') -Destination $InstallDir
Copy-Item -Force -Path (Join-Path $here 'collectorv2.ps1') -Destination $InstallDir
Copy-Item -Force -Path (Join-Path $here 'collector-loop.ps1') -Destination $InstallDir

$loopPath = Join-Path $InstallDir 'collector-loop.ps1'

# Build arguments for loop
$loopArgs = "-NoProfile -ExecutionPolicy Bypass -File `"$loopPath`" -IntervalSeconds $IntervalSeconds -ScriptName $ScriptName"

# Create Scheduled Task
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $loopArgs
$trigger = New-ScheduledTaskTrigger -AtStartup
if ($RunAsSystem) {
  $principal = New-ScheduledTaskPrincipal -UserId "NT AUTHORITY\SYSTEM" -RunLevel Highest
} else {
  $principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -RunLevel Highest
}

$existing = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($existing) { Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false }

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Principal $principal `
  -Description "Run Proxmox collector every $IntervalSeconds seconds via loop" | Out-Null

Start-ScheduledTask -TaskName $TaskName
Write-Host "Collector installed and started. Task='$TaskName' Dir='$InstallDir' Script='$ScriptName' Interval=${IntervalSeconds}s"

