# ==== CAUREQ Ops Board - Proxmox Collector (FINAL v4.1 / PS 5.1) ====
# - GET via curl.exe (TLS ok, redirections ok)
# - QEMU : tente l'IP via /agent/network-get-interfaces (Guest Agent)
# - LXC  : tente l'IP via /status/current
# - Nettoyage valeurs (arrays/null) + payload propre
# - Log clair + corps d'erreur en cas de 4xx

$ErrorActionPreference = 'Stop'

# ---------- CONFIG ----------
$PVEBase = "https://192.168.1.86:8006"            # URL Proxmox
$TokenId = "root@pam!opsboard"                    # API Token ID
$Secret  = "40838c09-7673-422d-9370-3114ee613c1f" # API Token secret
$Backend = "http://localhost:8060/api/ingest"     # Endpoint backend
$ApiKey  = "CHANGEMOI123"                         # = app.api-key
$Log     = "C:\OpsBoard\Agent\collector-proxmox.log"

# ---------- LOG ----------
function Write-Log($msg) {
  $ts = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
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
  if ($http -ne "200") { throw "HTTP $http for $url" }
  try {
    return $body | ConvertFrom-Json
  } catch {
    $preview = $body.Substring(0, [Math]::Min(160, $body.Length))
    Write-Log ("RAW (first 160): " + $preview)
    throw "Response was not JSON for $url"
  }
}

# QEMU : IPv4 via Guest Agent (si actif)
function Get-GuestIPv4($node, $vmid) {
  try {
    $resp = CurlJson "$PVEBase/api2/json/nodes/$node/qemu/$vmid/agent/network-get-interfaces"
    $data = $resp.data
    $ifaces = $data.result
    if (-not $ifaces) { $ifaces = $data }
    $all = @()
    foreach($iface in $ifaces){
      $ips = $iface.'ip-addresses'
      if (-not $ips) { continue }
      foreach($ipobj in $ips){
        $ip = $ipobj.'ip-address'
        $type = $ipobj.'ip-address-type'
        if ($type -eq 'ipv4' -and $ip -and $ip -ne '127.0.0.1' -and $ip -match '^(?:\d{1,3}\.){3}\d{1,3}$') {
          $all += $ip
        }
      }
    }
    # Prioriser RFC1918 (10/172.16-31/192.168)
    $priv = $all | Where-Object { $_ -like '10.*' -or $_ -like '192.168.*' -or ($_ -match '^172\.(1[6-9]|2[0-9]|3[0-1])\..*') } | Select-Object -First 1
    if ($priv) { return $priv }
    # Sinon, 1re IPv4 non-loopback
    if ($all.Count -gt 0) { return $all[0] }
  } catch {
 Write-Log ("GA IP fetch failed vmid={0}: {1}" -f $vmid, $_.Exception.Message)
  }
  return $null
}

# QEMU : Filesystem usage via Guest Agent (get-fsinfo)
function Get-FsUsageQga($node, $vmid) {
  $map = @{}
  try {
    $resp = CurlJson "$PVEBase/api2/json/nodes/$node/qemu/$vmid/agent/get-fsinfo"
    $data = $resp.data
    $list = $data.result
    if (-not $list) { $list = $data }
    foreach($fs in $list){
      $mp = $fs.'mountpoint'
      $fst = $fs.'fstype'
      $total = [double]($fs.'total-bytes')
      $used  = [double]($fs.'used-bytes')
      if ([string]::IsNullOrWhiteSpace($mp)) { continue }
      if ($fst -in @('tmpfs','proc','sysfs','devtmpfs')) { continue }
      if ($total -le 0) { continue }
      $pct = [math]::Round(($used/$total)*100,1)
      if ($mp -match '^[A-Za-z]:\\\\$') { $mp = $mp.Substring(0,3) }
      $map[$mp] = $pct
    }
  } catch {}
  return $map
}

# LXC : tente /status/current -> d.network -> IPv4
function Get-LxcIPv4($node, $vmid) {
  try {
    $resp = CurlJson "$PVEBase/api2/json/nodes/$node/lxc/$vmid/status/current"
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
  } catch {
   Write-Log ("LXC IP fetch failed vmid={0}: {1}" -f $vmid, $_.Exception.Message)
  }
  return $null
}

# POST /api/ingest (ip toujours présente; log en cas d'erreur)
function Post-AssetMetric($hostname, $ip, $os, $cpu, $ram, $disk){
  $cpu  = [math]::Round([double]$cpu, 1)
  $ram  = [math]::Round([double]$ram, 1)
  $disk = [math]::Round([double]$disk, 1)

  # s'assurer que 'ip' existe et ressemble à IPv4
  $ipStr = $ip
  if ([string]::IsNullOrWhiteSpace($ipStr) -or ($ipStr -notmatch '^(?:\d{1,3}\.){3}\d{1,3}$')) {
    $ipStr = '0.0.0.0'
  }
# -- à la place de $payload actuel --
$svc = @{}
# facultatif : indiquer l’état du GA si VM QEMU
if ($os -eq "Proxmox-VM") {
    if ([string]::IsNullOrWhiteSpace($ip)) {
        $svc["qemu-ga"] = "unknown"
    } else {
        $svc["qemu-ga"] = "up"
    }
}

$payload = [ordered]@{
  hostname = $hostname
  ip       = $ipStr
  os       = $os
  cpu      = $cpu
  ram      = $ram
  disk     = $disk
  services = $svc  # <— important : jamais null
}
  $json = $payload | ConvertTo-Json -Depth 5

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
    throw
  }
}

# ---------- Main ----------
try{
  Write-Log "START Proxmox collector"

  $nodes = (CurlJson "$PVEBase/api2/json/nodes").data
  if (-not $nodes) { throw "No node returned by Proxmox" }

  foreach($n in $nodes){
    $node = $n.node
    Write-Log ("Node={0} status={1}" -f $node, $n.status)

    # --- QEMU VMs ---
    $qemu = (CurlJson "$PVEBase/api2/json/nodes/$node/qemu").data
    $qCount = ($qemu | Measure-Object | Select-Object -ExpandProperty Count)
    Write-Log ("QEMU count={0}" -f $qCount)

    foreach($vm in $qemu){
      $vmid   = $vm.vmid
      $name   = $vm.name

      $mem     = AsDouble $vm.mem
      $maxmem  = AsDouble $vm.maxmem
      $disk    = AsDouble $vm.disk
      $maxdisk = AsDouble $vm.maxdisk
      $cpuFrac = AsDouble $vm.cpu
      # Normaliser CPU via /status/current si dispo (pour connaître 'cpus')
      $cpuPct = $null
      try {
        $cur = CurlJson "$PVEBase/api2/json/nodes/$node/qemu/$vmid/status/current"
        if (-not $cur.__error) {
          $rawCpu = [double]$cur.data.cpu
          $cpus   = [double]$cur.data.cpus
          if ($cpus -gt 0) { $cpuPct = [math]::Round((($rawCpu / $cpus) * 100), 1) }
          else { $cpuPct = [math]::Round(($rawCpu * 100), 1) }
          # Prefer /status/current for RAM/Disk too
          $ramPct = SafePct ([double]$cur.data.mem) ([double]$cur.data.maxmem)
          $dskPct = SafePct ([double]$cur.data.disk) ([double]$cur.data.maxdisk)
        }
      } catch {}
      if ($null -eq $cpuPct) { $cpuPct = [math]::Round($cpuFrac * 100, 1) }
      if ($null -eq $ramPct) { $ramPct = SafePct $mem $maxmem }
      if ($null -eq $dskPct) { $dskPct = SafePct $disk $maxdisk }

      # IP via Guest Agent (si dispo)
      $ip = Get-GuestIPv4 $node $vmid
      $ipLog = if ([string]::IsNullOrWhiteSpace($ip)) { 'null' } else { $ip }

      $hn = ("vm-{0}-{1}" -f $vmid, $name)
      $fs = Get-FsUsageQga $node $vmid
      if ($fs.Keys.Count -gt 0) {
        if ($fs.ContainsKey('/')) { $dskPct = [double]$fs['/'] }
        elseif ($fs.ContainsKey('C:\')) { $dskPct = [double]$fs['C:\'] }
      }
      # enrich services with fs usage
      $svc = @{}
      foreach($k in $fs.Keys){ $svc["fs:"+$k] = ("{0}" -f $fs[$k]) }
      Post-AssetMetric -hostname $hn -ip $ip -os "Proxmox-VM" -cpu $cpuPct -ram $ramPct -disk $dskPct
      Write-Log ("QEMU {0} ip={1} cpu={2} ram={3} disk={4} fs={5}" -f $hn, $ipLog, $cpuPct, $ramPct, $dskPct, ($fs.Keys -join ','))
    }

    # --- LXC containers ---
    $lxc = (CurlJson "$PVEBase/api2/json/nodes/$node/lxc").data
    $cCount = ($lxc | Measure-Object | Select-Object -ExpandProperty Count)
    Write-Log ("LXC  count={0}" -f $cCount)

    foreach($ct in $lxc){
      $vmid   = $ct.vmid
      $name   = $ct.name

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

      $hn = ("ct-{0}-{1}" -f $vmid, $name)
      Post-AssetMetric -hostname $hn -ip $ip -os "Proxmox-CT" -cpu $cpuPct -ram $ramPct -disk $dskPct
      Write-Log ("LXC  {0} ip={1} cpu={2} ram={3} disk={4}" -f $hn, $ipLog, $cpuPct, $ramPct, $dskPct)
    }
  }

  Write-Log "DONE"
}
catch{
  Write-Log ("ERROR: " + $_.Exception.Message)
  throw
}
