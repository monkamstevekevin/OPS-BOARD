# C:\OpsBoard\Agent\testagent.ps1
# Test Proxmox QEMU Guest Agent via pveproxy, resilient to 596 Broken pipe.

# ===== TLS + self-signed handling (PS 5.1+ compatible) =====
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}
try {
  [System.Net.ServicePointManager]::ServerCertificateValidationCallback = { param($s,$c,$ch,$e) $true }
} catch {
  Add-Type @"
using System.Net;
using System.Security.Cryptography.X509Certificates;
public class TrustAllCertsPolicy : ICertificatePolicy {
  public bool CheckValidationResult(ServicePoint srvPoint, X509Certificate certificate, WebRequest request, int problem) { return true; }
}
"@
  [System.Net.ServicePointManager]::CertificatePolicy = New-Object TrustAllCertsPolicy
}

# ====== CONFIG ======
$BaseUrl   = "https://192.168.1.86:8006/api2/json"
$Node      = "Caureqlab"
$Vmid      = 206
$TokenId   = "root@pam!opsboard"
$TokenSec  = "40838c09-7673-422d-9370-3114ee613c1f"

$AuthHeader = "PVEAPIToken=$TokenId=$TokenSec"

# Turn off 'Expect: 100-Continue' globally to avoid proxy oddities
[System.Net.ServicePointManager]::Expect100Continue = $false

# Build common headers
function New-CommonHeaders {
  return @{
    "Authorization" = $AuthHeader
    "Accept"        = "application/json"
    "Connection"    = "close"               # <-- force non-keepalive to avoid 596
  }
}

function Invoke-ProxmoxForm {
  param(
    [Parameter(Mandatory)] [string] $Url,
    [Parameter(Mandatory)] [hashtable] $Form,
    [int] $TimeoutSec = 10,
    [int] $Retry = 1
  )

  # Manually encode x-www-form-urlencoded (no chunked, no multipart)
  $pairs = @()
  foreach ($k in $Form.Keys) {
    $v = [string]$Form[$k]
    $pairs += ("{0}={1}" -f [Uri]::EscapeDataString($k), [Uri]::EscapeDataString($v))
  }
  $BodyString = [string]::Join('&', $pairs)

  $headers = New-CommonHeaders

  for ($i=0; $i -le $Retry; $i++) {
    try {
      return Invoke-RestMethod -Uri $Url `
        -Method Post `
        -Headers $headers `
        -ContentType "application/x-www-form-urlencoded" `
        -Body $BodyString `
        -TimeoutSec $TimeoutSec
    } catch [System.Net.WebException] {
      $msg = $_.Exception.Message
      if ($i -lt $Retry -and ($msg -match '596' -or $msg -match 'Broken pipe')) {
        Write-Warning "596/Broken pipe detected; retrying once after short delay..."
        Start-Sleep -Milliseconds 400
        continue
      }
      throw "HTTP call failed: $msg"
    } catch {
      throw
    }
  }
}

function Invoke-ProxmoxGet {
  param(
    [Parameter(Mandatory)] [string] $Url,
    [int] $TimeoutSec = 10
  )
  try {
    return Invoke-RestMethod -Uri $Url -Method Get -Headers (New-CommonHeaders) -TimeoutSec $TimeoutSec
  } catch [System.Net.WebException] {
    throw "HTTP call failed: $($_.Exception.Message)"
  }
}

# ====== 1) Sanity: ping the agent via API ======
$pingUrl = "$BaseUrl/nodes/$Node/qemu/$Vmid/agent/ping"
try {
  $ping = Invoke-ProxmoxGet -Url $pingUrl -TimeoutSec 5
  Write-Host "Agent ping OK (API)."
} catch {
  Write-Warning "Agent ping FAILED (non-blocking). Msg: $($_.Exception.Message)"
}

# ====== 2) Exec a trivial command ======
# Proxmox expects: command=<JSON array string>  (e.g. ["\/bin\/sh","-lc","echo OK"])
$cmd = @("/bin/sh","-lc","echo OK")
$cmdJson = ($cmd | ConvertTo-Json -Compress)

$execUrl = "$BaseUrl/nodes/$Node/qemu/$Vmid/agent/exec"
$execForm = @{
  "command" = $cmdJson
  # "input-data" = ""   # uncomment if you need stdin
}

Write-Host "→ Exec in VM $Vmid on $Node : [$($cmd -join ' ')]"

try {
  $execResp = Invoke-ProxmoxForm -Url $execUrl -Form $execForm -TimeoutSec 10 -Retry 1
} catch {
  throw "Échec du POST /agent/exec : $($_.Exception.Message)"
}

# Response contains {"data":{"pid": <int>}}
$pid = $execResp.data.pid
if (-not $pid) { throw "No PID in response: $(ConvertTo-Json $execResp -Compress)" }

# ====== 3) Poll status (exited / out-data) ======
$statusUrl = "$BaseUrl/nodes/$Node/qemu/$Vmid/agent/exec-status?pid=$pid"

$deadline = [DateTime]::UtcNow.AddSeconds(10)
$last = $null
do {
  Start-Sleep -Milliseconds 350
  $last = Invoke-ProxmoxGet -Url $statusUrl -TimeoutSec 10
} while (($last.data.exited -ne 1) -and ([DateTime]::UtcNow -lt $deadline))

if ($last.data.exited -ne 1) {
  throw "Timeout waiting for exec-status (pid=$pid)"
}

$exit = $last.data.exitcode
$stdout = $last.data.'out-data'
$stderr = $last.data.'err-data'

Write-Host "PID=$pid  exit=$exit"
if ($stdout) { Write-Host "STDOUT:`n$stdout" }
if ($stderr) { Write-Host "STDERR:`n$stderr" }
