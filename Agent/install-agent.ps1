param(
  [string]$TaskName     = "OpsBoardAgent",
  [string]$ScriptPath   = "C:\OpsBoard\Agent\agent.ps1",
  [int]   $EveryMins    = 2,
  [int]   $DurationDays = 365,
  [switch]$RunAsSystem
)

if (-not (Test-Path $ScriptPath)) { throw "Script introuvable: $ScriptPath" }

# Action : lancer PowerShell avec le script
$Action = New-ScheduledTaskAction -Execute "powershell.exe" `
  -Argument "-NoProfile -ExecutionPolicy Bypass -File `"`"$ScriptPath`"`""

# Départ dans 1 minute + répétition (pas de MaxValue)
$start   = (Get-Date).AddMinutes(1)
$Trigger = New-ScheduledTaskTrigger -Once -At $start `
  -RepetitionInterval (New-TimeSpan -Minutes $EveryMins) `
  -RepetitionDuration (New-TimeSpan -Days $DurationDays)

# Principal
if ($RunAsSystem) {
  # => OBLIGE PowerShell "Exécuter en tant qu’administrateur"
  $Principal = New-ScheduledTaskPrincipal -UserId "NT AUTHORITY\SYSTEM" -RunLevel Highest
} else {
  # Utilisateur courant, niveau limité (pas besoin d'admin)
  $Principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited
}

# (Ré)installer proprement
$existing = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($existing) { Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false }

Register-ScheduledTask -TaskName $TaskName -Action $Action -Trigger $Trigger -Principal $Principal `
  -Description "Send metrics to Ops Board every $EveryMins minute(s) for $DurationDays day(s)" | Out-Null

Start-ScheduledTask -TaskName $TaskName
Write-Host "Tâche '$TaskName' installée/démarrée. Mode: " -NoNewline
if ($RunAsSystem) { Write-Host "SYSTEM (admin)" } else { Write-Host "Utilisateur courant (Interactive)" }
