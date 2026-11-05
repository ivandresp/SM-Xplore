# Verificacion rapida del stack integrado (compose.yaml raiz).
# Uso: pwsh scripts/smoke.ps1
param(
  [string]$HostName = "localhost",
  [int]$WaitSeconds = 300
)

$ErrorActionPreference = "Stop"

# Si existe .env, respeta los puertos configurados alli.
if (Test-Path ".env") {
  foreach ($line in Get-Content ".env") {
    $trimmed = $line.Trim()
    if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) { continue }
    $index = $trimmed.IndexOf("=")
    if ($index -lt 1) { continue }
    $key = $trimmed.Substring(0, $index).Trim()
    $value = $trimmed.Substring($index + 1).Trim().Trim([char]34).Trim([char]39)
    if (-not [Environment]::GetEnvironmentVariable($key)) {
      [Environment]::SetEnvironmentVariable($key, $value)
    }
  }
}

function Get-Port([string]$name, [int]$default) {
  $value = [Environment]::GetEnvironmentVariable($name)
  if ($value) { return [int]$value }
  return $default
}

$ports = @{
  "ms-01" = (Get-Port "MS01_PORT" 8081)
  "ms-02" = (Get-Port "MS02_PORT" 8080)
  "ms-03" = (Get-Port "MS03_PORT" 8082)
  "ms-04" = (Get-Port "MS04_PORT" 8084)
  "ms-05" = (Get-Port "MS05_PORT" 8085)
}
$failures = 0

function Pass($message) { Write-Host "  OK   $message" -ForegroundColor Green }
function Fail($message) { Write-Host "  FAIL $message" -ForegroundColor Red; $script:failures++ }

function Wait-Health([string]$name, [int]$port) {
  $url = "http://" + $HostName + ":" + $port + "/actuator/health"
  $deadline = (Get-Date).AddSeconds($WaitSeconds)
  Write-Host "Esperando $name ..."
  while ((Get-Date) -lt $deadline) {
    try {
      $response = Invoke-RestMethod -Uri $url -TimeoutSec 3
      if ($response.status -eq "UP") { Pass "$name -> $url"; return }
    } catch { }
    Start-Sleep -Seconds 2
  }
  Fail "$name no respondio UP en $WaitSeconds s ($url)"
}

Write-Host "== Salud de los servicios =="
foreach ($name in $ports.Keys) { Wait-Health $name $ports[$name] }

$ms01 = $ports["ms-01"]
$ms03 = $ports["ms-03"]
$ms04 = $ports["ms-04"]

Write-Host "== JWKS de ms-01 =="
try {
  $jwks = Invoke-RestMethod -Uri ("http://" + $HostName + ":" + $ms01 + "/.well-known/jwks.json") -TimeoutSec 5
  if ($jwks.keys.Count -ge 1) { Pass "JWKS publica al menos una llave" } else { Fail "JWKS vacio" }
} catch { Fail "JWKS inaccesible: $_" }

Write-Host "== Catalogo publico de ms-03 =="
foreach ($path in @("/api/v1/activities", "/api/v1/events")) {
  try {
    $response = Invoke-WebRequest -Uri ("http://" + $HostName + ":" + $ms03 + $path) -TimeoutSec 5 -UseBasicParsing
    if ($response.StatusCode -eq 200) { Pass "GET $path responde 200" } else { Fail "GET $path respondio $($response.StatusCode)" }
  } catch { Fail "GET $path fallo: $_" }
}

Write-Host "== Autenticacion y JWT entre servicios =="
try {
  $email = if ($env:BOOTSTRAP_ADMIN_EMAIL) { $env:BOOTSTRAP_ADMIN_EMAIL } else { "admin@smxplore.local" }
  $password = if ($env:BOOTSTRAP_ADMIN_PASSWORD) { $env:BOOTSTRAP_ADMIN_PASSWORD } else { "ChangeMe123!" }
  $loginBody = @{ email = $email; password = $password } | ConvertTo-Json
  $login = Invoke-RestMethod -Uri ("http://" + $HostName + ":" + $ms01 + "/api/v1/auth/login") -Method Post -ContentType "application/json" -Body $loginBody -TimeoutSec 10
  if ($login.accessToken) {
    Pass "login del administrador devuelve accessToken"
    $headers = @{ Authorization = "Bearer " + $login.accessToken }
    $sites = Invoke-WebRequest -Uri ("http://" + $HostName + ":" + $ms04 + "/api/v1/sites") -Headers $headers -TimeoutSec 5 -UseBasicParsing
    if ($sites.StatusCode -eq 200) { Pass "ms-04 valida el JWT emitido por ms-01 (GET /api/v1/sites)" } else { Fail "ms-04 respondio $($sites.StatusCode)" }
    $reservations = Invoke-WebRequest -Uri ("http://" + $HostName + ":" + $ms03 + "/api/v1/reservations?status=CONFIRMED") -Headers $headers -TimeoutSec 5 -UseBasicParsing
    if ($reservations.StatusCode -eq 200) { Pass "ms-03 valida el JWT con su JWKS (GET /api/v1/reservations)" } else { Fail "ms-03 respondio $($reservations.StatusCode)" }
  } else { Fail "login sin accessToken" }
} catch { Fail "login/JWT fallo: $_" }

Write-Host "== Herramientas =="
$tools = @(
  @("Kafka UI", (Get-Port "KAFKA_UI_PORT" 8090)),
  @("Mailpit", (Get-Port "MAILPIT_UI_PORT" 8025)),
  @("Neo4j", (Get-Port "NEO4J_HTTP_PORT" 7474))
)
foreach ($tool in $tools) {
  $label = $tool[0]
  $port = $tool[1]
  try {
    $response = Invoke-WebRequest -Uri ("http://" + $HostName + ":" + $port + "/") -TimeoutSec 5 -UseBasicParsing
    if ($response.StatusCode -eq 200 -or $response.StatusCode -eq 302) { Pass "$label responde en $port" } else { Fail "$label codigo $($response.StatusCode)" }
  } catch { Fail "$label no responde: $_" }
}

Write-Host ""
if ($failures -eq 0) { Write-Host "Smoke OK: el stack esta arriba."; exit 0 }
Write-Host "Smoke con $failures fallo(s). Revisa: docker compose ps && docker compose logs"
exit 1
