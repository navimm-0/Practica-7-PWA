$ErrorActionPreference = "Stop"

$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if (-not $maven) {
    $maven = Get-Command mvn -ErrorAction SilentlyContinue
}
if (-not $maven) {
    throw "Maven is not installed or is not available in PATH."
}

$pom = Join-Path $PSScriptRoot "pom.xml"
$services = @(
    @{
        MainClass = "backend.ServidorAlumnos"
        Name = "Servidor Alumnos"
        Url = "http://localhost:8001/alumnos"
    },
    @{
        MainClass = "com.biblioteca.libros.ServidorLibros"
        Name = "Servidor Libros"
        Url = "http://localhost:6001/libros"
    },
    @{
        MainClass = "com.biblioteca.orquestador.ServidorOrquestador"
        Name = "Servidor Orquestador"
        Url = "http://localhost:8080/api/alumnos"
    }
)
$processes = @()

try {
    foreach ($service in $services) {
        Write-Host "Iniciando $($service.Name)..."
        $process = Start-Process -FilePath $maven.Source `
            -ArgumentList @("-f", "`"$pom`"", "spring-boot:run", "-Dspring-boot.run.main-class=$($service.MainClass)") `
            -PassThru -NoNewWindow
        $processes += $process

        $ready = $false
        for ($attempt = 0; $attempt -lt 60; $attempt++) {
            if ($process.HasExited) {
                throw "$($service.Name) stopped before startup. Check the Maven error."
            }
            try {
                Invoke-WebRequest -Uri $service.Url -TimeoutSec 2 | Out-Null
                $ready = $true
                break
            }
            catch {
                Start-Sleep -Seconds 1
            }
        }

        if (-not $ready) {
            throw "Timed out waiting for $($service.Name) to start."
        }
        Write-Host "$($service.Name) disponible en $($service.Url)"
    }

    Write-Host "All services are running. Press Ctrl+C to stop them."
    while ($processes | Where-Object { -not $_.HasExited }) {
        Start-Sleep -Seconds 1
    }
}
finally {
    foreach ($process in $processes) {
        if (-not $process.HasExited) {
            Stop-Process -Id $process.Id -Force
        }
    }
}
