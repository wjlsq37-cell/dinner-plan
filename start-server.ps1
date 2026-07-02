$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$jdkHome = "C:\Users\admin\.jdks\ms-17.0.19"
$envFile = Join-Path $projectRoot ".env"
$serverEnvFile = Join-Path $projectRoot "server\.env"

if (-not (Test-Path (Join-Path $jdkHome "bin\java.exe"))) {
    throw "JDK 17 not found at $jdkHome"
}

if (-not (Test-Path $envFile) -and -not (Test-Path $serverEnvFile)) {
    Write-Warning "No .env file found. Copy .env.example to .env and fill AI_BASE_URL, AI_API_KEY, AI_MODEL, and AMAP_WEB_KEY."
}

function Import-DotEnv($path) {
    if (-not (Test-Path $path)) {
        return
    }
    Get-Content $path | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#") -or -not $line.Contains("=")) {
            return
        }
        $name = $line.Substring(0, $line.IndexOf("=")).Trim()
        $value = $line.Substring($line.IndexOf("=") + 1).Trim().Trim('"')
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

Import-DotEnv $envFile
Import-DotEnv $serverEnvFile

$serverPort = 8080
if ($env:PORT) {
    $configuredPort = $env:PORT -as [int]
    if ($configuredPort -gt 0) {
        $serverPort = $configuredPort
    }
}

Write-Host "AI_BASE_URL configured: $([bool]$env:AI_BASE_URL)"
Write-Host "AI_API_KEY configured: $([bool]$env:AI_API_KEY)"
Write-Host "AI_MODEL configured: $([bool]$env:AI_MODEL)"
Write-Host "AMAP_WEB_KEY configured: $([bool]$env:AMAP_WEB_KEY)"
Write-Host "Backend port: $serverPort"

$recipeJsonl = if ($env:RECIPE_CORPUS_JSONL) { $env:RECIPE_CORPUS_JSONL } else { Join-Path $projectRoot "food\recipe_corpus_full.json" }
$recipeDb = if ($env:RECIPE_CORPUS_DB) { $env:RECIPE_CORPUS_DB } else { Join-Path $projectRoot "server\data\recipe_corpus.sqlite" }
if (-not [System.IO.Path]::IsPathRooted($recipeJsonl)) {
    $recipeJsonl = Join-Path $projectRoot $recipeJsonl
}
if (-not [System.IO.Path]::IsPathRooted($recipeDb)) {
    $recipeDb = Join-Path $projectRoot $recipeDb
}
[Environment]::SetEnvironmentVariable("RECIPE_CORPUS_JSONL", $recipeJsonl, "Process")
[Environment]::SetEnvironmentVariable("RECIPE_CORPUS_DB", $recipeDb, "Process")
Write-Host "Recipe corpus JSONL: $recipeJsonl"
Write-Host "Recipe corpus DB: $recipeDb"

function Stop-ExistingBackend($port) {
    $connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if (-not $connections) {
        Write-Host "No existing backend found on port $port."
        return
    }

    $processIds = $connections |
        Select-Object -ExpandProperty OwningProcess -Unique |
        Where-Object { $_ -and $_ -gt 0 }

    foreach ($processId in $processIds) {
        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if (-not $process) {
            continue
        }

        if ($process.ProcessName -notin @("java", "javaw")) {
            throw "Port $port is in use by non-backend process $($process.ProcessName) ($processId). Stop it manually before starting the backend."
        }

        Write-Host "Stopping existing backend process $($process.ProcessName) ($processId) on port $port..."
        Stop-Process -Id $processId -Force
    }

    $deadline = (Get-Date).AddSeconds(10)
    do {
        Start-Sleep -Milliseconds 300
        $stillListening = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    } while ($stillListening -and (Get-Date) -lt $deadline)

    if ($stillListening) {
        throw "Port $port is still in use after stopping the previous backend."
    }
}

Stop-ExistingBackend $serverPort

$env:JAVA_HOME = $jdkHome
$env:Path = "$jdkHome\bin;$env:Path"
$env:GRADLE_USER_HOME = Join-Path $projectRoot ".gradle-user-home"

Set-Location $projectRoot
if (Test-Path $recipeJsonl) {
    $needsRecipeImport = -not (Test-Path $recipeDb)
    if (-not $needsRecipeImport) {
        $jsonInfo = Get-Item -LiteralPath $recipeJsonl
        $dbInfo = Get-Item -LiteralPath $recipeDb
        $needsRecipeImport = $jsonInfo.LastWriteTimeUtc -gt $dbInfo.LastWriteTimeUtc
    }

    if ($needsRecipeImport) {
        Write-Host "Importing local recipe corpus. First import may take a while..."
        .\gradlew-jdk17.bat :server:importRecipeCorpus
    } else {
        Write-Host "Recipe corpus index is up to date."
    }
} else {
    Write-Warning "Recipe corpus JSONL not found. Backend will still start, but database search will use fallback recipes."
}

Write-Host "Building updated backend package..."
.\gradlew-jdk17.bat :server:installDist

$serverStartScript = Join-Path $projectRoot "server\build\install\server\bin\server.bat"
if (-not (Test-Path $serverStartScript)) {
    throw "Backend start script was not generated: $serverStartScript"
}

Write-Host "Starting backend. Open http://localhost:$serverPort/health after the server is ready."
& $serverStartScript
