# ==== CAUREQ Ops Board – Agent Windows v2 (PS 5.1 compatible) ====
$ErrorActionPreference = 'Stop'

# ====== CONFIG ======
$ApiUrl        = "http://localhost:8060/api/ingest"     # URL backend
$ApiKey        = "CHANGEMOI123"                          # doit matcher app.api-key côté serveur
$Log           = "C:\OpsBoard\Agent\agent.log"
$PreferredCidrs = @("192.168.*","10.*")                  # priorités IP si présentes (optionnel)
$ServiceNames   = @("Spooler")                           # services à vérifier (ajoute: "LanmanWorkstation", etc.)

# ====== LOG ======
function Write-Log($msg) {
  $stamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
  $dir = Split-Path $Log
  if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
  Add-Content -Path $Log -Value "[$stamp] $msg"
}

# ====== HELPERS ======
function Get-OsCaption {
  try { (Get-CimInstance Win32_OperatingSystem).Caption } catch { "Windows" }
}

function Is-BadAlias([string]$alias) {
  $bad = @('vEthernet','Hyper-V','WSL','Docker','VirtualBox','TAP','VPN','Loopback','Bluetooth')
  foreach ($b in $bad) { if ($alias -like "*$b*") { return $true } }
  return $false
}

function Get-PrimaryIPv4 {
  # 1) route par défaut (0.0.0.0/0), meilleure métrique
  $def = Get-NetRoute -DestinationPrefix "0.0.0.0/0" -ErrorAction SilentlyContinue |
         Sort-Object -Property RouteMetric, InterfaceMetric
  foreach ($r in $def) {
    $ad = Get-NetAdapter -InterfaceIndex $r.ifIndex -ErrorAction SilentlyContinue
    if ($ad -and $ad.Status -eq 'Up' -and -not (Is-BadAlias $ad.InterfaceAlias)) {
      $ip = Get-NetIPAddress -AddressFamily IPv4 -InterfaceIndex $ad.InterfaceIndex -ErrorAction SilentlyContinue |
            Where-Object { $_.IPAddress -notlike '169.*' } |
            Select-Object -First 1 -ExpandProperty IPAddress
      if ($ip) { return $ip }
    }
  }

  # 2) priorité par CIDR si dispo
  if ($PreferredCidrs -and $PreferredCidrs.Count -gt 0) {
    $all = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
           Where-Object {
             $_.IPAddress -match '^\d{1,3}(\.\d{1,3}){3}$' -and
             $_.IPAddress -notlike '169.*'
           } |
           Sort-Object InterfaceIndex
    foreach ($p in $PreferredCidrs) {
      $found = $all | Where-Object { $_.IPAddress -like $p } | Select-Object -First 1
      if ($found) {
        $ad = Get-NetAdapter -InterfaceIndex $found.InterfaceIndex -ErrorAction SilentlyContinue
        if ($ad -and $ad.Status -eq 'Up' -and -not (Is-BadAlias $ad.InterfaceAlias)) {
          return $found.IPAddress
        }
      }
    }
  }

  # 3) fallback: 1ère IPv4 "propre" sur interface up non virtuelle
  $any = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
         Where-Object {
           $_.IPAddress -match '^\d{1,3}(\.\d{1,3}){3}$' -and
           $_.IPAddress -notlike '169.*' -and
           ($ad = (Get-NetAdapter -InterfaceIndex $_.InterfaceIndex -ErrorAction SilentlyContinue)) -and
           $ad.Status -eq 'Up' -and
           -not (Is-BadAlias $ad.InterfaceAlias)
         } |
         Select-Object -First 1 -ExpandProperty IPAddress
  if ($any) { return $any }

  return "127.0.0.1"
}

function Get-SystemDriveUsagePercent {
  $sys = $env:SystemDrive.TrimEnd('\')
  $drv = Get-PSDrive -PSProvider FileSystem | Where-Object { $_.Name -eq $sys.TrimEnd(':') }
  if ($drv) {
    $total = $drv.Used + $drv.Free
    if ($total -gt 0) { return [math]::Round(($drv.Used / $total) * 100, 1) }
  }
  return 0
}

function Get-ServicesState($names) {
  $map = @{}
  foreach ($n in $names) {
    try {
      $svc = Get-Service -Name $n -ErrorAction Stop
      $map[$n] = $(if ($svc.Status -eq 'Running') { 'up' } else { 'down' })
    } catch { $map[$n] = 'missing' }
  }
  return $map
}

# ====== RUN ======
Write-Log "START agent run"

try {
  $Hostname = $env:COMPUTERNAME
  $IP       = Get-PrimaryIPv4
  $OS       = Get-OsCaption

  # CPU %
  $CPUavg = (Get-CimInstance Win32_Processor | Measure-Object -Property LoadPercentage -Average).Average
  $CPU    = $(if ($null -eq $CPUavg) { 0 } else { [double]$CPUavg })

  # RAM %
  $Mem = Get-CimInstance Win32_OperatingSystem
  $RAM = [math]::Round( ( ($Mem.TotalVisibleMemorySize - $Mem.FreePhysicalMemory) / $Mem.TotalVisibleMemorySize ) * 100, 1 )

  # DISK % (lecteur système)
  $Disk = Get-SystemDriveUsagePercent

  # SERVICES
  $services = Get-ServicesState $ServiceNames

  # Payload
  $body = @{
    hostname = $Hostname
    ip       = $IP
    os       = $OS
    cpu      = $CPU
    ram      = $RAM
    disk     = $Disk
    services = $services
  } | ConvertTo-Json -Depth 6

  # Envoi
  Invoke-RestMethod -Method Post -Uri $ApiUrl `
    -Headers @{ "X-API-KEY" = $ApiKey } `
    -ContentType "application/json" -Body $body | Out-Null

  Write-Log "OK host=$Hostname ip=$IP os='$OS' cpu=$CPU ram=$RAM disk=$Disk"
}
catch {
  $msg = $_.Exception.Message
  Write-Log "ERROR: $msg"
  Write-Error $msg
  exit 1
}
