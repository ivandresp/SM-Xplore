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

Write-Host "== Metricas (Prometheus) =="
# Tras la auditoria final, ms-01 (perfil dev), ms-03 y ms-04 exponen /actuator/prometheus en
# desarrollo; ms-02 y ms-05 lo exigen autenticado (401/403), que tambien prueba que el servicio
# esta arriba y que el endpoint no quedo abierto.
$metricsExpectation = @{ "ms-01" = 200; "ms-02" = "protegido"; "ms-03" = 200; "ms-04" = 200; "ms-05" = "protegido" }
foreach ($name in $ports.Keys) {
  $url = "http://" + $HostName + ":" + $ports[$name] + "/actuator/prometheus"
  $expected = $metricsExpectation[$name]
  try {
    $response = Invoke-WebRequest -Uri $url -TimeoutSec 5 -UseBasicParsing
    if ($expected -eq 200) {
      if ($response.StatusCode -eq 200) { Pass "$name expone /actuator/prometheus" } else { Fail "$name /actuator/prometheus respondio $($response.StatusCode)" }
    } else { Fail "$name /actuator/prometheus respondio $($response.StatusCode) (se esperaba 401/403)" }
  } catch {
    $code = 0
    if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    if ($expected -ne 200 -and ($code -eq 401 -or $code -eq 403)) {
      Pass "$name protege /actuator/prometheus (codigo $code)"
    } else {
      Fail "$name /actuator/prometheus fallo: $_"
    }
  }
}

Write-Host "== Flujo de negocio (catalogo -> actividad -> resena -> reserva) =="
function Invoke-SmokeRequest([string]$url, [string]$token, [object]$payload, [string]$method = "Post") {
  $params = @{ Uri = $url; Method = $method; ContentType = "application/json"; Body = ($payload | ConvertTo-Json -Depth 6); TimeoutSec = 20; SkipHttpErrorCheck = $true }
  if ($token) { $params.Headers = @{ Authorization = "Bearer " + $token } }
  $response = Invoke-WebRequest @params -UseBasicParsing
  [pscustomobject]@{ Status = [int]$response.StatusCode; Body = $response.Content }
}

function Invoke-SmokePost([string]$url, [string]$token, [object]$payload) {
  Invoke-SmokeRequest -url $url -token $token -payload $payload
}
$flowToken = $null
if ($login) { $flowToken = $login.accessToken }
$categoryId = ""
$zoneId = ""
$siteId = ""
$activityId = ""
$touristToken = $null
$reservationId = ""
if (-not $flowToken) {
  Fail "sin token de administrador no se puede recorrer el flujo de negocio"
} else {
  $stamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
  $base04 = "http://" + $HostName + ":" + $ports["ms-04"]
  $base03 = "http://" + $HostName + ":" + $ports["ms-03"]
  $invariant = [System.Globalization.CultureInfo]::InvariantCulture
  $startsAt = (Get-Date).ToUniversalTime().AddDays(3).ToString("yyyy-MM-dd'T'HH:mm:ss'Z'", $invariant)
  # El dominio exige endsAt > startsAt.
  $endsAt = (Get-Date).ToUniversalTime().AddDays(3).AddHours(2).ToString("yyyy-MM-dd'T'HH:mm:ss'Z'", $invariant)

  $created = Invoke-SmokePost ($base04 + "/api/v1/categories") $flowToken @{ name = "Smoke categoria $stamp"; scope = "ACTIVITY" }
  if ($created.Status -eq 201 -or $created.Status -eq 200) { $categoryId = ($created.Body | ConvertFrom-Json).categoryId; Pass "ms-04 creo la categoria ($categoryId)" } else { Fail "ms-04 no creo la categoria (HTTP $($created.Status)): $($created.Body)" }

  $created = Invoke-SmokePost ($base04 + "/api/v1/zones") $flowToken @{ name = "Smoke zona $stamp"; type = "URBANA"; description = "zona del smoke" }
  if ($created.Status -eq 201 -or $created.Status -eq 200) { $zoneId = ($created.Body | ConvertFrom-Json).zoneId; Pass "ms-04 creo la zona ($zoneId)" } else { Fail "ms-04 no creo la zona (HTTP $($created.Status)): $($created.Body)" }

  if ($categoryId -and $zoneId) {
    $created = Invoke-SmokePost ($base04 + "/api/v1/sites") $flowToken @{ name = "Smoke sitio $stamp"; description = "sitio del smoke"; categoryId = $categoryId; zoneId = $zoneId; latitude = 11.2408; longitude = -74.1990; maxCapacity = 20 }
    if ($created.Status -eq 201 -or $created.Status -eq 200) { $siteId = ($created.Body | ConvertFrom-Json).siteId; Pass "ms-04 creo el sitio ($siteId)" } else { Fail "ms-04 no creo el sitio (HTTP $($created.Status)): $($created.Body)" }
  } else { Fail "sin categoria/zona no se puede crear el sitio" }

  if ($siteId) {
    $created = Invoke-SmokePost ($base03 + "/api/v1/activities") $flowToken @{ siteId = $siteId; name = "Smoke actividad $stamp"; category = "AVENTURA"; price = 25.00; maxCapacity = 10; durationMinutes = 120; startsAt = $startsAt; endsAt = $endsAt; description = "actividad del smoke" }
    if ($created.Status -eq 201 -or $created.Status -eq 200) {
      $activityId = ($created.Body | ConvertFrom-Json).id
      Pass "ms-03 creo la actividad ($activityId)"
      # Nace en DRAFT: sin publicarla el catalogo da 404 y la resena/reserva 400.
      # El endpoint de estado es PATCH, no POST.
      $published = Invoke-SmokeRequest -url ($base03 + "/api/v1/activities/" + $activityId + "/status") -token $flowToken -payload @{ status = "PUBLISHED" } -method "Patch"
      if (($published.Body | ConvertFrom-Json).status -eq "PUBLISHED") { Pass "la actividad quedo PUBLISHED" } else { Fail "no se pudo publicar la actividad (HTTP $($published.Status)): $($published.Body)" }
    } else { Fail "ms-03 no creo la actividad (HTTP $($created.Status)): $($created.Body)" }
  }

  if ($activityId) {
    try {
      $response = Invoke-WebRequest -Uri ($base03 + "/api/v1/activities/" + $activityId) -TimeoutSec 5 -UseBasicParsing
      if ($response.StatusCode -eq 200) { Pass "GET publico de la actividad responde 200" } else { Fail "GET publico de la actividad respondio $($response.StatusCode)" }
    } catch { Fail "GET publico de la actividad fallo: $_" }
  }

  # Turista real: registro publico y login. Telefono y DNI unicos por corrida: en local el
  # volumen persiste y un telefono fijo haria que el segundo smoke devuelva 409.
  $touristEmail = "smoke-$stamp@smxplore.local"
  $registered = Invoke-SmokePost ("http://" + $HostName + ":" + $ports["ms-01"] + "/api/v1/auth/register/turista") $null @{ fullName = "Smoke Turista $stamp"; email = $touristEmail; phone = "3$stamp"; password = "SmokePass12345"; dniType = "CC"; dniNumber = "$stamp"; preferredLanguage = "es"; birthDate = "1990-01-01"; nationality = "CO" }
  $touristId = ""
  if ($registered.Status -eq 201 -or $registered.Status -eq 200) { $touristId = ($registered.Body | ConvertFrom-Json).id; Pass "ms-01 registro al turista ($touristId)" } else { Fail "ms-01 no registro al turista (HTTP $($registered.Status)): $($registered.Body)" }

  $touristLogin = Invoke-SmokePost ("http://" + $HostName + ":" + $ports["ms-01"] + "/api/v1/auth/login") $null @{ email = $touristEmail; password = "SmokePass12345" }
  if ($touristLogin.Status -eq 200) { $touristToken = ($touristLogin.Body | ConvertFrom-Json).accessToken }
  if ($touristToken) { Pass "login del turista devuelve accessToken" } else { Fail "login del turista fallo (HTTP $($touristLogin.Status)): $($touristLogin.Body)" }

  # MS03-13: para resenar hace falta una reserva CONFIRMED/COMPLETED del turista sobre el item,
  # asi que primero se reserva y despues se resena.
  if ($touristToken -and $activityId) {
    $reservation = Invoke-SmokePost ($base03 + "/api/v1/reservations") $touristToken @{ touristId = $touristId; itemType = "ACTIVITY"; itemId = $activityId; numberOfPeople = 1; paymentMethod = "CARD"; bookedFor = $startsAt }
    if ($reservation.Status -eq 201 -or $reservation.Status -eq 200) {
      $reservationId = ($reservation.Body | ConvertFrom-Json).id
      Pass "el turista creo la reserva ($reservationId)"
      try {
        $own = Invoke-WebRequest -Uri ($base03 + "/api/v1/reservations/" + $reservationId) -Headers @{ Authorization = "Bearer " + $touristToken } -TimeoutSec 5 -UseBasicParsing
        if ($own.StatusCode -eq 200) { Pass "GET de la reserva propia responde 200" } else { Fail "GET de la reserva propia respondio $($own.StatusCode)" }
      } catch { Fail "GET de la reserva propia fallo: $_" }

      $review = Invoke-SmokePost ($base03 + "/api/v1/reviews") $touristToken @{ itemType = "ACTIVITY"; itemId = $activityId; rating = 5; comment = "resena del smoke" }
      if ($review.Status -eq 201 -or $review.Status -eq 200) { Pass "el turista publico una resena" } else { Fail "la resena respondio $($review.Status): $($review.Body)" }
    } else { Fail "la reserva respondio $($reservation.Status): $($reservation.Body)" }
  }

  # El outbox es la prueba de que el evento salio: se consulta si hay docker disponible.
  if ((Test-Path "compose.yaml") -and (Get-Command docker -ErrorAction SilentlyContinue)) {
    $pgUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "smxplore" }
    $published = (docker compose exec -T postgres psql -U $pgUser -d activities_events -tAc "SELECT count(*) FROM outbox_events WHERE status = 'PUBLISHED'" 2>$null | Out-String).Trim()
    if ($published -match "^\d+$" -and [int]$published -gt 0) { Pass "el outbox de ms-03 publico $published evento(s)" } else { Fail "el outbox de ms-03 no registro eventos publicados" }
  } else { Write-Host "  SKIP outbox: sin docker en PATH o fuera del directorio del compose" }
}

Write-Host "== Herramientas =="
$tools = @(
  @("Kafka UI", (Get-Port "KAFKA_UI_PORT" 8090)),
  @("Mailpit", (Get-Port "MAILPIT_UI_PORT" 8025)),
  @("Neo4j", (Get-Port "NEO4J_HTTP_PORT" 7474))
)
foreach ($tool in $tools) {
  $label = $tool[0]
  $port = $tool[1]
  # PLAT-08: Kafka UI y Mailpit piden basic auth; el 401 confirma que estan arriba
  # y protegidas. En PS 5.1 Invoke-WebRequest lanza excepcion con 401, asi que se
  # inspecciona la respuesta del error.
  try {
    $response = Invoke-WebRequest -Uri ("http://" + $HostName + ":" + $port + "/") -TimeoutSec 5 -UseBasicParsing
    if ($response.StatusCode -eq 200 -or $response.StatusCode -eq 302 -or $response.StatusCode -eq 401) { Pass "$label responde en $port" } else { Fail "$label codigo $($response.StatusCode)" }
  } catch {
    $status = $null
    if ($_.Exception.Response -ne $null) { $status = [int]$_.Exception.Response.StatusCode }
    if ($status -eq 401) { Pass "$label responde en $port (auth requerida)" } else { Fail "$label no responde: $_" }
  }
}

Write-Host ""
if ($failures -eq 0) { Write-Host "Smoke OK: el stack esta arriba."; exit 0 }
Write-Host "Smoke con $failures fallo(s). Revisa: docker compose ps && docker compose logs"
exit 1