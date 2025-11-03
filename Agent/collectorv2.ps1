# ==== CAUREQ Ops Board - Proxmox Collector (FINAL v5) ====
# - GET via curl.exe (TLS ok, redirections ok)
# - QEMU : IP via agent/network-get-interfaces (si agent OK)
# - QEMU : CPU/RAM/DISK via /status/current (fiable)
# - LXC  : IP via /status/current
# - Services: expose "qemu-ga" = up|down|unknown
# - Log robuste, pas de throw sur 500 GA (comportement normal si GA off)
# - Envoi backend /api/ingest (X-API-KEY), ip toujours présente (0.0.0.0 si inconnue)

param(
  [string]$PVEBase = "https://192.168.1.86:8006",
  [string]$TokenId = "root@pam!opsboard",
  [string]$Secret  = "40838c09-7673-422d-9370-3114ee613c1f",
  [string]$Backend = "http://localhost:8060/api/ingest",
  [string]$ApiKey  = "CHANGEMOI123",
  [string]$Log     = "C:\OpsBoard\Agent\collector-proxmox.log"
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# ---------- LOG ----------
function Write-Log($msg) {
  $ts  = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
  $dir = Split-Path $Log
  if (-not (Test-Path $dir)) { New-Item -Force -ItemType Directory -Path $dir | Out-Null }
  Add-Content -Path $Log -Value "[$ts] $msg"
}

# ---------- TLS pour Invoke-* (curl.exe n'en a pas besoin) ----------
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
if (-not [System.Net.ServicePointManager]::ServerCertificateValidationCallback) {
  [System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }
}

# ---------- Helpers ----------
function SafePct([double]$num, [double]$den) {
  if ($den -le 0) { return 0.0 }
  [math]::Round(($num / $den) * 100.0, 1)
}

function AsDouble($v) {
  if ($null -eq $v) { return [double]0 }
  if ($v -is [array]) {
    if ($v.Length -gt 0) { return [double]$v[0] } else { return [double]0 }
  }
  return [double]$v
}

# GET JSON via curl.exe (-k ignore cert, -L follow redirects); log HTTP code
function CurlJson($url){
  $cmd = "curl.exe -s -k -L -H `"Authorization: PVEAPIToken=$TokenId=$Secret`" `"$url`" -w `" HTTP:%{http_code}`""
  $out = cmd /c $cmd
  if (-not $out) { throw "curl.exe returned empty output for $url" }
  $http = ($out -split ' HTTP:')[-1]
  $body = $out.Substring(0, $out.Length - (" HTTP:$http").Length)
  Write-Log ("GET {0} -> HTTP {1}" -f $url, $http)
  if ($http -ne "200") {
    # Retourne un objet indiquant l'erreur pour que l'appelant choisisse de continuer
    return [pscustomobject]@{ __http = $http; __error = $true; __raw = $body }
  }
  try {
    return $body | ConvertFrom-Json
  } catch {
    $preview = $body.Substring(0, [Math]::Min(160, $body.Length))
    Write-Log ("RAW (first 160): " + $preview)
    throw "Response was not JSON for $url"
  }
}

# ---------- Proxmox helpers ----------

# QEMU: test du Guest Agent
function Is-QgaUp($node, $vmid) {
  $resp = CurlJson "$PVEBase/api2/json/nodes/$node/qemu/$vmid/agent/ping"
  if ($resp.__error) {
    Write-Log ("GA ping failed vmid={0}: HTTP {1}" -f $vmid, $resp.__http)
    return $false
  }
  return $true
}

# QEMU: IPv4 via Guest Agent (si up)
function Get-GuestIPv4($node, $vmid) {
  $resp = CurlJson "$PVEBase/api2/json/nodes/$node/qemu/$vmid/agent/network-get-interfaces"
  if ($resp.__error) {
    Write-Log ("GA IP fetch failed vmid={0}: HTTP {1} for {2}" -f $vmid, $resp.__http, "$PVEBase/api2/json/nodes/$node/qemu/$vmid/agent/network-get-interfaces")
    return $null
  }
  $data = $resp.data
  $ifaces = $data.result
  if (-not $ifaces) { $ifaces = $data }
  $all = @()
  foreach($iface in $ifaces){
    $ips = $iface.'ip-addresses'
    if (-not $ips) { continue }
    foreach($ipobj in $ips){
      $ip   = $ipobj.'ip-address'
      $type = $ipobj.'ip-address-type'
      if ($type -eq 'ipv4' -and $ip -and $ip -ne '127.0.0.1' -and $ip -match '^(?:\d{1,3}\.){3}\d{1,3}$') {
        $all += $ip
      }
    }
  }
  $priv = $all | Where-Object { $_ -like '10.*' -or $_ -like '192.168.*' -or ($_ -match '^172\.(1[6-9]|2[0-9]|3[0-1])\..*') } | Select-Object -First 1
  if ($priv) { return $priv }
  if ($all.Count -gt 0) { return $all[0] }
  return $null
}

# QEMU: stats fiables (cpu/mem/disk) via /status/current
function Get-QemuStats($node, $vmid) {
  $resp = CurlJson "$PVEBase/api2/json/nodes/$node/qemu/$vmid/status/current"
  if ($resp.__error) {
    return @{ cpu=0; ram=0; disk=0 }
  }
  $d = $resp.data
  $rawCpu = [double]$d.cpu
  $cpus   = [double]$d.cpus
  # Proxmox 'cpu' est la somme sur tous les vCPU en unités de CPU (ex: 1.8 sur 2 vCPU)
  # On normalise systématiquement sur 0-100% capacité VM
  if ($cpus -gt 0) {
    $cpuPct = [math]::Round((($rawCpu / $cpus) * 100), 1)
  } else {
    $cpuPct = [math]::Round(($rawCpu * 100), 1)
  }
  $ramPct = SafePct ([double]$d.mem) ([double]$d.maxmem)
  $dskPct = SafePct ([double]$d.disk) ([double]$d.maxdisk)
  return @{ cpu=$cpuPct; ram=$ramPct; disk=$dskPct }
}

# LXC: IPv4 depuis /status/current
function Get-LxcIPv4($node, $vmid) {
  $resp = CurlJson "$PVEBase/api2/json/nodes/$node/lxc/$vmid/status/current"
  if ($resp.__error) {
    Write-Log ("LXC IP fetch failed vmid={0}: HTTP {1}" -f $vmid, $resp.__http)
    return $null
  }
  $d = $resp.data
  if ($d -and $d.ip -and $d.ip -match '^(?:\d{1,3}\.){3}\d{1,3}$' -and $d.ip -ne '127.0.0.1') {
    return $d.ip
  }
  if ($d -and $d.network) {
    foreach($name in $d.network.PSObject.Properties.Name){
      $ni = $d.network.$name
      if ($ni){
        if ($ni.ip -and $ni.ip -match '^(?:\d{1,3}\.){3}\d{1,3}$' -and $ni.ip -ne '127.0.0.1') { return $ni.ip }
        if ($ni.ipv4) {
          foreach($entry in $ni.ipv4){
            $ip = ($entry -split '/')[0]
            if ($ip -match '^(?:\d{1,3}\.){3}\d{1,3}$' -and $ip -ne '127.0.0.1'){ return $ip }
          }
        }
      }
    }
  }
  return $null
}

# ---------- POST backend (/api/ingest) ----------
function Post-AssetMetric($hostname, $ip, $os, $cpu, $ram, $disk, $services){
  $cpu  = [math]::Round([double]$cpu, 1)
  $ram  = [math]::Round([double]$ram, 1)
  $disk = [math]::Round([double]$disk, 1)

  # ip toujours présente (fallback 0.0.0.0)
  $ipStr = $ip
  if ([string]::IsNullOrWhiteSpace($ipStr) -or ($ipStr -notmatch '^(?:\d{1,3}\.){3}\d{1,3}$')) {
    $ipStr = '0.0.0.0'
  }

  if ($null -eq $services) { $services = @{} }

  $payload = [ordered]@{
    hostname = $hostname
    ip       = $ipStr
    os       = $os
    cpu      = $cpu
    ram      = $ram
    disk     = $disk
    services = $services
  }
  $json = $payload | ConvertTo-Json -Depth 6

  try {
    Invoke-RestMethod -Method Post -Uri $Backend `
      -Headers @{ "X-API-KEY" = $ApiKey } `
      -ContentType "application/json" -Body $json | Out-Null
  } catch {
    $respText = ""
    if ($_.Exception.Response) {
      try {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $respText = $reader.ReadToEnd()
      } catch {}
    }
    Write-Log ("INGEST error: " + $respText)
    Write-Log ("INGEST payload: " + $json)
    # on log l'erreur, mais on ne throw pas pour continuer la collecte globale
  }
}

# ---------- Main ----------
try{
  Write-Log "START Proxmox collector"

  # NODES
  $nodesResp = CurlJson "$PVEBase/api2/json/nodes"
  if ($nodesResp.__error) { throw "No node returned by Proxmox (HTTP $($nodesResp.__http))" }
  $nodes = $nodesResp.data
  if (-not $nodes) { throw "No node returned by Proxmox" }

  foreach($n in $nodes){
    $node   = $n.node
    $nState = $n.status
    Write-Log ("Node={0} status={1}" -f $node, $nState)

    # --- QEMU VMs ---
    $qResp = CurlJson "$PVEBase/api2/json/nodes/$node/qemu"
    if ($qResp.__error) {
      Write-Log ("Skip QEMU on node={0} (HTTP {1})" -f $node, $qResp.__http)
      continue
    }
    $qemu = $qResp.data
    $qCount = ($qemu | Measure-Object | Select-Object -ExpandProperty Count)
    Write-Log ("QEMU count={0}" -f $qCount)

    foreach($vm in $qemu){
      $vmid = $vm.vmid
      $name = $vm.name
      $hn   = ("vm-{0}-{1}" -f $vmid, $name)

      # état initial depuis la liste
      $state = $vm.status
      # si inconnu, tente status/current pour affiner
      if ([string]::IsNullOrWhiteSpace($state) -or $state -eq 'unknown') {
        $cur = CurlJson "$PVEBase/api2/json/nodes/$node/qemu/$vmid/status/current"
        if (-not $cur.__error) {
          $qmp = $cur.data.qmpstatus
          if ($qmp) { $state = $qmp }
        }
      }

      $cpuPct = 0; $ramPct = 0; $dskPct = 0
      $ip = $null
      $svc = @{}

      if ($state -eq 'running') {
        # stats fiables
        $stats = Get-QemuStats $node $vmid
        $cpuPct = $stats.cpu
        $ramPct = $stats.ram
        $dskPct = $stats.disk

        # QGA
        $qgaUp = Is-QgaUp $node $vmid
        $svc['qemu-ga'] = if ($qgaUp) { 'up' } else { 'down' }

        if ($qgaUp) {
          $ip = Get-GuestIPv4 $node $vmid
        } else {
          $ip = $null
        }
      } else {
        # VM arrêtée / paused / etc.
        $svc['qemu-ga'] = 'unknown'
      }

      Post-AssetMetric -hostname $hn -ip $ip -os "Proxmox-VM" -cpu $cpuPct -ram $ramPct -disk $dskPct -services $svc
      $ipLog = if ([string]::IsNullOrWhiteSpace($ip)) { 'null' } else { $ip }
      Write-Log ("QEMU {0} state={5} ip={1} cpu={2} ram={3} disk={4}" -f $hn, $ipLog, $cpuPct, $ramPct, $dskPct, $state)
    }

    # --- LXC containers ---
    $cResp = CurlJson "$PVEBase/api2/json/nodes/$node/lxc"
    if ($cResp.__error) {
      Write-Log ("Skip LXC on node={0} (HTTP {1})" -f $node, $cResp.__http)
      continue
    }
    $lxc = $cResp.data
    $cCount = ($lxc | Measure-Object | Select-Object -ExpandProperty Count)
    Write-Log ("LXC  count={0}" -f $cCount)

    foreach($ct in $lxc){
      $vmid = $ct.vmid
      $name = $ct.name
      $hn   = ("ct-{0}-{1}" -f $vmid, $name)

      # stats depuis la liste (si vides, tu peux aussi appeler /status/current pour lxc si nécessaire)
      $mem     = AsDouble $ct.mem
      $maxmem  = AsDouble $ct.maxmem
      $disk    = AsDouble $ct.disk
      $maxdisk = AsDouble $ct.maxdisk
      $cpuFrac = AsDouble $ct.cpu

      $cpuPct = [math]::Round($cpuFrac * 100, 1)
      $ramPct = SafePct $mem $maxmem
      $dskPct = SafePct $disk $maxdisk

      $ip = Get-LxcIPv4 $node $vmid
      $ipLog = if ([string]::IsNullOrWhiteSpace($ip)) { 'null' } else { $ip }

      # Pour LXC, pas de qemu-ga
      $svc = @{}

      Post-AssetMetric -hostname $hn -ip $ip -os "Proxmox-CT" -cpu $cpuPct -ram $ramPct -disk $dskPct -services $svc
      Write-Log ("LXC  {0} ip={1} cpu={2} ram={3} disk={4}" -f $hn, $ipLog, $cpuPct, $ramPct, $dskPct)
    }
  }

  Write-Log "DONE"
}
catch{
  Write-Log ("ERROR: " + $_.Exception.Message)
  throw
}
