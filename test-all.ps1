#Requires -Version 5.1
<#
  Hotel Booking Microservices - end-to-end API checks through the Gateway.
  Prerequisite: Eureka (8761), Hotel (8081), Booking (8082), Gateway (8080) running.
  Run from any directory:  .\test-all.ps1
  Optional JUnit/MockMvc: .\test-all.ps1 -RunMavenTests -ProjectRoot "C:\path\hotel-booking-microservices"
  This script creates test records and cancels/deletes most of them afterwards.
  v2: consistent JSON-array handling for Windows PowerShell 5.1 and PowerShell 7.
#>
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$EurekaUrl = 'http://localhost:8761',
    [string]$ProjectRoot = $PSScriptRoot,
    [switch]$RunMavenTests
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$BaseUrl = $BaseUrl.TrimEnd('/')
$EurekaUrl = $EurekaUrl.TrimEnd('/')
$script:passed = 0
$script:failed = 0
$script:demoHeaders = $null
$script:adminHeaders = $null
$script:booking = $null
$script:autoBooking = $null
$script:createdHotel = $null
$script:createdRoom = $null
$script:createdUser = $null
$script:registeredUser = $null
$script:manualRequestId = [guid]::NewGuid().ToString()
$script:startDate = (Get-Date).Date.AddDays(14).ToString('yyyy-MM-dd')
$script:endDate = (Get-Date).Date.AddDays(16).ToString('yyyy-MM-dd')
$script:autoStart = (Get-Date).Date.AddDays(24).ToString('yyyy-MM-dd')
$script:autoEnd = (Get-Date).Date.AddDays(26).ToString('yyyy-MM-dd')
$script:runSuffix = ([guid]::NewGuid().ToString('N')).Substring(0, 10)

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Run-Check {
    param([string]$Name, [scriptblock]$Action)
    try {
        & $Action
        $script:passed++
        Write-Host "[PASS] $Name" -ForegroundColor Green
    }
    catch {
        $script:failed++
        Write-Host "[FAIL] $Name : $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Api {
    param(
        [string]$Path,
        [string]$Method = 'GET',
        [hashtable]$Headers = @{},
        $Body = $null
    )
    $params = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        Headers = $Headers
        ErrorAction = 'Stop'
    }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json'
        $params.Body = ConvertTo-Json -InputObject $Body -Depth 8 -Compress
    }
    return Invoke-RestMethod @params
}


# Invoke-RestMethod on Windows PowerShell 5.1 may return a JSON array as a
# *single* pipeline object. Wrapping it in @() then creates a nested array.
# Flatten only collection responses; keep POST/PUT response objects unchanged.
function Expand-ApiRows {
    param($Value)
    if ($null -eq $Value) { return }
    if ($Value -is [System.Array]) {
        foreach ($item in $Value) { Expand-ApiRows -Value $item }
    }
    else {
        Write-Output -InputObject $Value
    }
}

function Api-List {
    param(
        [string]$Path,
        [hashtable]$Headers = @{}
    )
    $response = Api -Path $Path -Headers $Headers
    Expand-ApiRows -Value $response
}

function Assert-HttpStatus {
    param(
        [int]$Expected,
        [string]$Path,
        [string]$Method = 'GET',
        [hashtable]$Headers = @{},
        $Body = $null
    )
    $params = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        Headers = $Headers
        ErrorAction = 'Stop'
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json'
        $params.Body = ConvertTo-Json -InputObject $Body -Depth 8 -Compress
    }
    $actual = $null
    try {
        $response = Invoke-WebRequest @params
        $actual = [int]$response.StatusCode
    }
    catch {
        if ($null -eq $_.Exception.Response) { throw }
        $actual = [int]$_.Exception.Response.StatusCode
    }
    if ($actual -ne $Expected) {
        throw "Expected HTTP $Expected but received HTTP $actual ($Method $Path)"
    }
}

function Require-Demo {
    if ($null -eq $script:demoHeaders) { throw 'USER login failed; no USER token' }
}
function Require-Admin {
    if ($null -eq $script:adminHeaders) { throw 'ADMIN login failed; no ADMIN token' }
}

Write-Host ''
Write-Host '=== HOTEL BOOKING: END-TO-END TESTS ===' -ForegroundColor Cyan
Write-Host "Gateway: $BaseUrl"
Write-Host "Dates for room booking: $script:startDate .. $script:endDate (end exclusive)"
Write-Host 'Running services are required; this script does not launch them.'
Write-Host ''

# 1. Startup / discovery
Run-Check 'Eureka dashboard responds (8761)' {
    $r = Invoke-WebRequest -Uri $EurekaUrl -UseBasicParsing -ErrorAction Stop
    Assert-True ($r.StatusCode -eq 200) 'Eureka did not return HTTP 200'
}
foreach ($svc in @(@('Hotel',8081),@('Booking',8082),@('Gateway',8080))) {
    $name = [string]$svc[0]
    $port = [int]$svc[1]
    Run-Check "$name health check (port $port)" {
        $h = Invoke-RestMethod "http://localhost:$port/actuator/health" -ErrorAction Stop
        Assert-True ($h.status -eq 'UP') 'Status is not UP'
    }
}
Run-Check 'All services registered in Eureka' {
    $data = Invoke-RestMethod -Uri "$EurekaUrl/eureka/apps" -Headers @{ Accept = 'application/json' } -ErrorAction Stop
    $names = @($data.applications.application | ForEach-Object { $_.name })
    foreach ($expected in @('BOOKING-SERVICE','HOTEL-SERVICE','API-GATEWAY')) {
        Assert-True ($names -contains $expected) "$expected not present in Eureka"
    }
}

if ($script:failed -gt 0) {
    Write-Host ''
    Write-Host 'Some services are not ready. Fix the startup/discovery checks above and rerun.' -ForegroundColor Yellow
    Write-Host "Passed: $script:passed; failed: $script:failed"
    exit 1
}

# 2. Authentication / authorization
Run-Check 'USER login returns a JWT for demo' {
    $r = Api -Path '/api/user/auth' -Method POST -Body @{ username='demo'; password='demo123' }
    Assert-True (-not [string]::IsNullOrWhiteSpace($r.accessToken)) 'accessToken is empty'
    Assert-True ($r.expiresInSeconds -eq 3600) 'JWT expiration must be 3600 seconds'
    $script:demoHeaders = @{ Authorization = "Bearer $($r.accessToken)" }
}
Run-Check 'ADMIN login returns a JWT for admin' {
    $r = Api -Path '/api/user/auth' -Method POST -Body @{ username='admin'; password='admin123' }
    Assert-True (-not [string]::IsNullOrWhiteSpace($r.accessToken)) 'admin accessToken is empty'
    $script:adminHeaders = @{ Authorization = "Bearer $($r.accessToken)" }
}
Run-Check 'Wrong password -> 401' {
    Assert-HttpStatus -Expected 401 -Path '/api/user/auth' -Method POST -Body @{ username='demo'; password='incorrect-password' }
}
Run-Check 'Request without token -> 401' {
    Assert-HttpStatus -Expected 401 -Path '/api/bookings'
}
Run-Check 'USER cannot read ADMIN room statistics -> 403' {
    Require-Demo
    Assert-HttpStatus -Expected 403 -Path '/api/rooms/stats' -Headers $script:demoHeaders
}
Run-Check 'Gateway blocks internal room release -> 403' {
    Require-Admin
    Assert-HttpStatus -Expected 403 -Path '/api/rooms/1/release' -Method POST -Headers $script:adminHeaders -Body @{ bookingId=123456; compensate=$true }
}

# 3. Hotel catalogue, availability and recommendations
Run-Check 'Seed hotels are visible through Gateway' {
    Require-Demo
    $script:hotels = @(Api-List -Path '/api/hotels' -Headers $script:demoHeaders)
    Assert-True ($script:hotels.Count -ge 1) 'No hotels returned'
}
Run-Check 'Available rooms returned for chosen dates' {
    Require-Demo
    $script:rooms = @(Api-List -Path "/api/rooms?startDate=$script:startDate&endDate=$script:endDate" -Headers $script:demoHeaders)
    Assert-True ($script:rooms.Count -ge 1) 'No available rooms for test dates'
    Assert-True ($null -ne $script:rooms[0].id) 'Room id is missing'
    $script:roomId = [long]$script:rooms[0].id
}
Run-Check 'Recommendations sorted by timesBooked ASC, then id ASC' {
    Require-Demo
    $rows = @(Api-List -Path "/api/rooms/recommend?startDate=$script:startDate&endDate=$script:endDate" -Headers $script:demoHeaders)
    Assert-True ($rows.Count -gt 0) 'No recommended rooms'
    $sorted = @($rows | Sort-Object -Property @{Expression={[long]$_.timesBooked};Ascending=$true}, @{Expression={[long]$_.id};Ascending=$true})
    Assert-True (($rows.id -join ',') -eq ($sorted.id -join ',')) 'Wrong recommendation order'
}
Run-Check 'Invalid room date range -> 400' {
    Require-Demo
    Assert-HttpStatus -Expected 400 -Path "/api/rooms?startDate=$script:endDate&endDate=$script:startDate" -Headers $script:demoHeaders
}

# 4. Booking life cycle: create, conflict, idempotency, cancel
Run-Check 'Create booking -> CONFIRMED' {
    Require-Demo
    Assert-True ($script:roomId -gt 0) 'No roomId'
    $script:manualBody = @{ roomId=$script:roomId; autoSelect=$false; startDate=$script:startDate; endDate=$script:endDate }
    $h = @{ Authorization=$script:demoHeaders.Authorization; 'X-Request-Id'=$script:manualRequestId }
    $script:booking = Api -Path '/api/booking' -Method POST -Headers $h -Body $script:manualBody
    Assert-True ($script:booking.status -eq 'CONFIRMED') "Unexpected booking status: $($script:booking.status)"
    Assert-True ([long]$script:booking.roomId -eq $script:roomId) 'Wrong room assigned'
}
Run-Check 'Booked room is no longer free on these dates' {
    Require-Demo
    Assert-True ($null -ne $script:booking) 'Booking was not created'
    $rows = @(Api-List -Path "/api/rooms?startDate=$script:startDate&endDate=$script:endDate" -Headers $script:demoHeaders)
    Assert-True (@($rows | Where-Object { $_.id -eq $script:roomId }).Count -eq 0) 'Booked room is still listed as free'
}
Run-Check 'Overlapping booking with a NEW requestId -> 409' {
    Require-Demo
    Assert-True ($null -ne $script:booking) 'Booking was not created'
    $h = @{ Authorization=$script:demoHeaders.Authorization; 'X-Request-Id'=[guid]::NewGuid().ToString() }
    Assert-HttpStatus -Expected 409 -Path '/api/booking' -Method POST -Headers $h -Body $script:manualBody
}
Run-Check 'Repeat same requestId returns same booking (no duplicate)' {
    Require-Demo
    Assert-True ($null -ne $script:booking) 'Booking was not created'
    $h = @{ Authorization=$script:demoHeaders.Authorization; 'X-Request-Id'=$script:manualRequestId }
    $second = Api -Path '/api/booking' -Method POST -Headers $h -Body $script:manualBody
    Assert-True ([long]$second.id -eq [long]$script:booking.id) 'Repeat request created another booking'
}
Run-Check 'Reusing requestId with different parameters -> 409' {
    Require-Demo
    Assert-True ($null -ne $script:booking) 'Booking was not created'
    $h = @{ Authorization=$script:demoHeaders.Authorization; 'X-Request-Id'=$script:manualRequestId }
    $altered = @{ roomId=$script:roomId; autoSelect=$false; startDate=$script:startDate; endDate=([datetime]::ParseExact($script:endDate,'yyyy-MM-dd',[System.Globalization.CultureInfo]::InvariantCulture)).AddDays(1).ToString('yyyy-MM-dd') }
    Assert-HttpStatus -Expected 409 -Path '/api/booking' -Method POST -Headers $h -Body $altered
}
Run-Check 'Booking history contains new booking' {
    Require-Demo
    Assert-True ($null -ne $script:booking) 'Booking was not created'
    $all = @(Api-List -Path '/api/bookings' -Headers $script:demoHeaders)
    Assert-True (@($all | Where-Object { $_.id -eq $script:booking.id }).Count -eq 1) 'Booking is missing from history'
}
Run-Check 'GET booking by ID -> CONFIRMED' {
    Require-Demo
    Assert-True ($null -ne $script:booking) 'Booking was not created'
    $b = Api -Path "/api/booking/$($script:booking.id)" -Headers $script:demoHeaders
    Assert-True ($b.status -eq 'CONFIRMED') 'Booking is not confirmed'
}
Run-Check 'Booking with an invalid date -> 400' {
    Require-Demo
    $h = @{ Authorization=$script:demoHeaders.Authorization; 'X-Request-Id'=[guid]::NewGuid().ToString() }
    $body = @{ roomId=$script:roomId; autoSelect=$false; startDate='2020-01-01'; endDate='2020-01-02' }
    Assert-HttpStatus -Expected 400 -Path '/api/booking' -Method POST -Headers $h -Body $body
}
Run-Check 'Cancel booking -> CANCELLED' {
    Require-Demo
    Assert-True ($null -ne $script:booking) 'Booking was not created'
    $cancelled = Api -Path "/api/booking/$($script:booking.id)" -Method DELETE -Headers $script:demoHeaders
    Assert-True ($cancelled.status -eq 'CANCELLED') 'Cancel did not produce CANCELLED'
}
Run-Check 'After cancellation the room is free again' {
    Require-Demo
    Assert-True ($null -ne $script:booking) 'Booking was not created'
    $rows = @(Api-List -Path "/api/rooms?startDate=$script:startDate&endDate=$script:endDate" -Headers $script:demoHeaders)
    Assert-True (@($rows | Where-Object { $_.id -eq $script:roomId }).Count -eq 1) 'Room was not released'
}
Run-Check 'Repeated cancellation stays CANCELLED' {
    Require-Demo
    Assert-True ($null -ne $script:booking) 'Booking was not created'
    $b = Api -Path "/api/booking/$($script:booking.id)" -Method DELETE -Headers $script:demoHeaders
    Assert-True ($b.status -eq 'CANCELLED') 'Repeated cancellation not idempotent'
}

# 5. Auto-selection
Run-Check 'Auto-selection picks the first recommended available room' {
    Require-Demo
    $recommend = @(Api-List -Path "/api/rooms/recommend?startDate=$script:autoStart&endDate=$script:autoEnd" -Headers $script:demoHeaders)
    Assert-True ($recommend.Count -gt 0) 'No candidate rooms for autoSelect'
    $h = @{ Authorization=$script:demoHeaders.Authorization; 'X-Request-Id'=[guid]::NewGuid().ToString() }
    $body = @{ startDate=$script:autoStart; endDate=$script:autoEnd; autoSelect=$true }
    $script:autoBooking = Api -Path '/api/booking' -Method POST -Headers $h -Body $body
    Assert-True ($script:autoBooking.status -eq 'CONFIRMED') 'Auto booking was not confirmed'
    Assert-True ([long]$script:autoBooking.roomId -eq [long]$recommend[0].id) 'Auto booking chose a different room'
}
Run-Check 'Cancel automatically selected booking' {
    Require-Demo
    Assert-True ($null -ne $script:autoBooking) 'Auto booking was not created'
    $b = Api -Path "/api/booking/$($script:autoBooking.id)" -Method DELETE -Headers $script:demoHeaders
    Assert-True ($b.status -eq 'CANCELLED') 'Auto booking cancellation failed'
}

# 6. Administrator CRUD (created objects are deleted afterwards)
Run-Check 'ADMIN create hotel' {
    Require-Admin
    $script:createdHotel = Api -Path '/api/hotels' -Method POST -Headers $script:adminHeaders -Body @{ name="Smoke Hotel $script:runSuffix"; address='Test street 10' }
    Assert-True ([long]$script:createdHotel.id -gt 0) 'Hotel id missing'
}
Run-Check 'ADMIN update hotel' {
    Require-Admin
    Assert-True ($null -ne $script:createdHotel) 'Test hotel was not created'
    $h = Api -Path "/api/hotels/$($script:createdHotel.id)" -Method PUT -Headers $script:adminHeaders -Body @{ name="Smoke Hotel Updated $script:runSuffix"; address='Test street 20' }
    Assert-True ($h.address -eq 'Test street 20') 'Hotel not updated'
}
Run-Check 'ADMIN create room' {
    Require-Admin
    Assert-True ($null -ne $script:createdHotel) 'Test hotel was not created'
    $script:createdRoom = Api -Path '/api/rooms' -Method POST -Headers $script:adminHeaders -Body @{ hotelId=$script:createdHotel.id; number="T-$script:runSuffix"; available=$true }
    Assert-True ([long]$script:createdRoom.id -gt 0) 'Room id missing'
}
Run-Check 'ADMIN update room' {
    Require-Admin
    Assert-True ($null -ne $script:createdRoom) 'Test room was not created'
    $r = Api -Path "/api/rooms/$($script:createdRoom.id)" -Method PUT -Headers $script:adminHeaders -Body @{ hotelId=$script:createdHotel.id; number="U-$script:runSuffix"; available=$false }
    Assert-True ($r.number -eq "U-$script:runSuffix") 'Room number not changed'
    Assert-True ($r.available -eq $false) 'Room availability not changed'
}
Run-Check 'ADMIN room listing contains created room' {
    Require-Admin
    Assert-True ($null -ne $script:createdRoom) 'Test room was not created'
    $all = @(Api-List -Path '/api/rooms/all' -Headers $script:adminHeaders)
    Assert-True (@($all | Where-Object { $_.id -eq $script:createdRoom.id }).Count -eq 1) 'New room not found'
}
Run-Check 'ADMIN room statistics endpoint works' {
    Require-Admin
    $stats = @(Api-List -Path '/api/rooms/stats' -Headers $script:adminHeaders)
    Assert-True ($stats.Count -gt 0) 'Statistics are empty'
    Assert-True ($null -ne $stats[0].timesBooked) 'timesBooked missing in statistics'
}
Run-Check 'ADMIN delete test room' {
    Require-Admin
    Assert-True ($null -ne $script:createdRoom) 'Test room was not created'
    Assert-HttpStatus -Expected 204 -Path "/api/rooms/$($script:createdRoom.id)" -Method DELETE -Headers $script:adminHeaders
    $script:createdRoom = $null
}
Run-Check 'ADMIN delete test hotel' {
    Require-Admin
    Assert-True ($null -ne $script:createdHotel) 'Test hotel was not created'
    Assert-HttpStatus -Expected 204 -Path "/api/hotels/$($script:createdHotel.id)" -Method DELETE -Headers $script:adminHeaders
    $script:createdHotel = $null
}
Run-Check 'ADMIN create user' {
    Require-Admin
    $script:createdUser = Api -Path '/api/user' -Method POST -Headers $script:adminHeaders -Body @{ username="mgr_$script:runSuffix"; password='secure1234'; role='USER' }
    Assert-True ($script:createdUser.role -eq 'USER') 'Created role should be USER'
}
Run-Check 'ADMIN update user role -> ADMIN' {
    Require-Admin
    Assert-True ($null -ne $script:createdUser) 'Test user was not created'
    $u = Api -Path "/api/user/$($script:createdUser.id)" -Method PATCH -Headers $script:adminHeaders -Body @{ role='ADMIN' }
    Assert-True ($u.role -eq 'ADMIN') 'Role was not updated'
}
Run-Check 'ADMIN can list users' {
    Require-Admin
    $users = @(Api-List -Path '/api/user' -Headers $script:adminHeaders)
    Assert-True (@($users | Where-Object { $_.id -eq $script:createdUser.id }).Count -eq 1) 'Created user not found'
}
Run-Check 'ADMIN delete test user' {
    Require-Admin
    Assert-True ($null -ne $script:createdUser) 'Test user was not created'
    Assert-HttpStatus -Expected 204 -Path "/api/user/$($script:createdUser.id)" -Method DELETE -Headers $script:adminHeaders
    $script:createdUser = $null
}

# 7. Registration of a new USER
Run-Check 'Register another USER and receive JWT' {
    $script:registeredUserName = "guest_$script:runSuffix"
    $r = Api -Path '/api/user/register' -Method POST -Body @{ username=$script:registeredUserName; password='guest1234' }
    Assert-True (-not [string]::IsNullOrWhiteSpace($r.accessToken)) 'Registration token missing'
    $script:registeredUser = $true
}
Run-Check 'New USER can authenticate' {
    Assert-True ($script:registeredUser -eq $true) 'Registration did not succeed'
    $r = Api -Path '/api/user/auth' -Method POST -Body @{ username=$script:registeredUserName; password='guest1234' }
    Assert-True (-not [string]::IsNullOrWhiteSpace($r.accessToken)) 'New USER authentication failed'
}
Run-Check 'Remove registration demo user using ADMIN' {
    Require-Admin
    Assert-True ($script:registeredUser -eq $true) 'Registration did not succeed'
    $users = @(Api-List -Path '/api/user' -Headers $script:adminHeaders)
    $testUser = @($users | Where-Object { $_.username -eq $script:registeredUserName })
    Assert-True ($testUser.Count -eq 1) 'Registered test user not found or duplicated'
    $registeredUserId = [long]$testUser[0].id
    Assert-HttpStatus -Expected 204 -Path "/api/user/$registeredUserId" -Method DELETE -Headers $script:adminHeaders
}

# 8. Optional unit/integration tests for concurrency, service errors and Saga compensation
if ($RunMavenTests) {
    Run-Check 'JUnit + MockMvc integration tests (mvn test)' {
        $pom = Join-Path $ProjectRoot 'pom.xml'
        Assert-True (Test-Path $pom) "pom.xml not found: $pom"
        $mvn = Get-Command mvn -ErrorAction SilentlyContinue
        Assert-True ($null -ne $mvn) 'Maven is not on PATH'
        Push-Location $ProjectRoot
        try {
            & mvn test
            Assert-True ($LASTEXITCODE -eq 0) "Maven tests exited with code $LASTEXITCODE"
        }
        finally { Pop-Location }
    }
}

Write-Host ''
Write-Host '================= RESULTS =================' -ForegroundColor Cyan
Write-Host "PASS: $script:passed" -ForegroundColor Green
Write-Host "FAIL: $script:failed" -ForegroundColor $(if ($script:failed -gt 0) { 'Red' } else { 'Green' })
if (-not $RunMavenTests) {
    Write-Host 'JUnit/MockMvc not run (add -RunMavenTests to include them).'
}
Write-Host 'H2 is in-memory: restart services to reset demo data.'
if ($script:failed -gt 0) { exit 1 }
exit 0
