# Verificacion rapida del stack integrado (compose.yaml raiz).
# Uso: pwsh scripts/smoke.ps1
param(
  [string]$HostName = "localhost",
  [int]$WaitSeconds = 300
)

$ErrorActionPreference = "Stop"
$ports = @{
  "ms-01" = 8081; "ms-02" = 8080; "ms-03" = 8082; "ms-04" = 8084; "ms-05" = 8085
}
$failures = 0

function Pass($message) { Write-Host "  OK   $message" -ForegroundColor Green }
function Fail($message) { Write-Host "  FAIL $message" -ForegroundColor Red; $script:failures++ }

function Wait-Health([string]$name, [int]$port) {
  $url = "http://${HostName}:${port}/actuator/health"
  $deadline = (Get-Date).AddSeconds($WaitSeconds)
  Write-Host "Esperando $name ..."
  while ((Get-Date) -lt $deadline) {
    try {
      $response = Invoke-RestMethod -Uri $url -TimeoutSec 3
      if ($response.status -eq "UP") { Pass "$name -> $url"; return }
    } catch { }
    Start-Sleep -Seconds 2
  }
  Fail "$name no respondio UP en ${WaitSeconds}s ($url)"
}

Write-Host "== Salud de los servicios =="
foreach ($name in $ports.Keys) { Wait-Health $name $ports[$name] }

Write-Host "== JWKS de ms-01 =="
try {
  $jwks = Invoke-RestMethod -Uri "http://${HostName}:8081/.well-known/jwks.json" -TimeoutSec 5
  if ($jwks.keys.Count -ge 1) { Pass "JWKS publica al menos una llave" } else { Fail "JWKS vacio" }
} catch { Fail "JWKS inaccesible: $_" }

Write-Host "== Catalogo publico de ms-03 =="
foreach ($path in @("/api/v1/activities", "/api/v1/events")) {
  try {
    $response = Invoke-WebRequest -Uri "http://${HostName}:8082$path" -TimeoutSec 5 -UseBasicParsing
    if ($response.StatusCode -eq 200) { Pass "GET $path responde 200" } else { Fail "GET $path respondio $($response.StatusCode)" }
  } catch { Fail "GET $path fallo: $_" }
}

Write-Host "== Autenticacion y JWT entre servicios =="
try {
  $email = if ($env:BOOTSTRAP_ADMIN_EMAIL) { $env:BOOTSTRAP_ADMIN_EMAIL } else { "admin@smxplore.local" }
  $password = if ($env:BOOTSTRAP_ADMIN_PASSWORD) { $env:BOOTSTRAP_ADMIN_PASSWORD } else { "ChangeMe123!" }
  $loginBody = @{ email = $email; password = $password } | ConvertTo-Json
  $login = Invoke-RestMethod -Uri "http://${HostName}:8081/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $loginBody -TimeoutSec 10
  if ($login.accessToken) {
    Pass "login del administrador devuelve accessToken"
    $headers = @{ Authorization = "Bearer $($login.accessToken)" }
    $sites = Invoke-WebRequest -Uri "http://${HostName}:8084/api/v1/sites" -Headers $headers -TimeoutSec 5 -UseBasicParsing
    if ($sites.StatusCode -eq 200) { Pass "ms-04 valida el JWT emitido por ms-01 (GET /api/v1/sites)" } else { Fail "ms-04 respondio $($sites.StatusCode)" }
  } else { Fail "login sin accessToken" }
} catch { Fail "login/JWT fallo: $_" }

Write-Host "== Herramientas =="
foreach ($tool in @(@("Kafka UI", 8090), @("Mailpit", 8025), @("Neo4j", 7474))) {
  try {
    $response = Invoke-WebRequest -Uri "http://${HostName}:$($tool[1])/" -TimeoutSec 5 -UseBasicParsing
    if ($response.StatusCode -in 200, 302) { Pass "$($tool[0]) responde en $($tool[1])" } else { Fail "$($tool[0]) codigo $($response.StatusCode)" }
  } catch { Fail "$($tool[0]) no responde: $_" }
}

Write-Host ""
if ($failures -eq 0) { Write-Host "Smoke OK: el stack esta arriba."; exit 0 }
Write-Host "Smoke con $failures fallo(s). Revisa: docker compose ps && docker compose logs"
exit 1