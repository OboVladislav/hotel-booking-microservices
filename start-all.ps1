# Запустить из корневой папки проекта после mvn clean package
$root = $PSScriptRoot
$services = @("discovery-server", "hotel-service", "booking-service", "api-gateway")
foreach ($service in $services) {
    $jar = Join-Path $root "$service/target/$service-1.0.0.jar"
    if (-not (Test-Path $jar)) { throw "Missing $jar. Run mvn clean package first." }
    Write-Host "Starting $service"
    Start-Process -FilePath "java" -ArgumentList @("-jar", ('"' + $jar + '"')) -WorkingDirectory $root
    Start-Sleep -Seconds 3
}
Write-Host "Gateway: http://localhost:8080 | Eureka: http://localhost:8761"
