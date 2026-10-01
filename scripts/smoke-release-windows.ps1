param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("msi", "jar")]
    [string] $Kind,
    [Parameter(Mandatory = $true)]
    [string] $Artifact
)

$ErrorActionPreference = "Stop"
$artifactPath = (Resolve-Path $Artifact).Path
if (-not (Test-Path $artifactPath -PathType Leaf) -or (Get-Item $artifactPath).Length -le 0) {
    throw "Release artifact must be a non-empty file"
}
$work = Join-Path $env:RUNNER_TEMP ("visual-agent-smoke-" + [guid]::NewGuid())
$profileDirectory = Join-Path $work "profile"
$dataRoot = Join-Path $work "server-data"
$installRoot = Join-Path $work "install"
$stdoutLog = Join-Path $work "application.stdout.log"
$stderrLog = Join-Path $work "application.stderr.log"
$applicationLog = Join-Path $work "application.log"
$app = $null
$installed = $false

New-Item -ItemType Directory -Force -Path $profileDirectory, $dataRoot, $installRoot | Out-Null
$env:USERPROFILE = $profileDirectory
$env:APPDATA = Join-Path $profileDirectory "AppData\Roaming"
$env:LOCALAPPDATA = Join-Path $profileDirectory "AppData\Local"
New-Item -ItemType Directory -Force -Path $env:APPDATA, $env:LOCALAPPDATA | Out-Null
$env:JAVA_TOOL_OPTIONS = "-Dvisualagent.startup.auto-start-local=true -Duser.home=`"$profileDirectory`" -Dvisual-agent.server.data-root=`"$dataRoot`" -Dlogging.file.name=`"$applicationLog`""

try {
    if ($Kind -eq "msi") {
        $env:JAVA_HOME = ""
        $env:PATH = "$env:SystemRoot\System32;$env:SystemRoot"
        if (Get-Command java.exe -ErrorAction SilentlyContinue) { throw "Native MSI smoke must run without a system Java executable" }
        $install = Start-Process msiexec.exe -ArgumentList @("/i", "`"$artifactPath`"", "/qn", "/norestart", "INSTALLDIR=`"$installRoot`"") -PassThru -Wait
        if ($install.ExitCode -ne 0) { throw "MSI installation failed with exit code $($install.ExitCode)" }
        $installed = $true
        $launcher = Get-ChildItem -Path $installRoot -Filter "Visual Agent.exe" -Recurse -File | Select-Object -First 1
        if ($null -eq $launcher) { throw "The installed Visual Agent launcher was not found under $installRoot" }
        $command = $launcher.FullName
    } else {
        $command = (Get-Command java.exe).Source
    }

    if ($Kind -eq "jar") {
        $app = Start-Process $command -ArgumentList @("-jar", "`"$artifactPath`"") -PassThru -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog
    } else {
        $app = Start-Process $command -PassThru -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog
    }

    $deadline = [DateTime]::UtcNow.AddMinutes(3)
    $ready = $false
    while ([DateTime]::UtcNow -lt $deadline) {
        $logs = @($stdoutLog, $applicationLog) | Where-Object { Test-Path $_ }
        if ($logs -and (Select-String -Path $logs -SimpleMatch "VISUAL_AGENT_DESKTOP_READY" -Quiet)) {
            $ready = $true
            break
        }
        if ($app.HasExited) { throw "Application exited before readiness with code $($app.ExitCode)" }
        Start-Sleep -Milliseconds 250
    }
    if (-not $ready) { throw "Desktop readiness marker was not emitted before the startup timeout" }
    $app.Refresh()
    if ($app.HasExited) { throw "Application exited immediately after readiness with code $($app.ExitCode)" }

    $database = Join-Path $dataRoot "visual-agent.db.mv.db"
    if (-not (Test-Path $database -PathType Leaf) -or (Get-Item $database).Length -le 0) {
        throw "The application did not create its isolated H2 database: $database"
    }
    if (-not (Test-Path (Join-Path $dataRoot "workspace") -PathType Container)) {
        throw "The application did not create its isolated workspace under $dataRoot"
    }
    $shutdownDeadline = [DateTime]::UtcNow.AddSeconds(30)
    while (-not $app.HasExited -and [DateTime]::UtcNow -lt $shutdownDeadline) {
        $app.Refresh()
        if ($app.MainWindowHandle -ne 0) { $null = $app.CloseMainWindow() }
        $null = $app.WaitForExit(250)
    }
    if (-not $app.HasExited) { throw "The application did not exit cleanly after closing its windows" }
    if ($app.ExitCode -ne 0) { throw "Application shutdown returned $($app.ExitCode)" }
    $app = $null
    Write-Host "Verified Windows $Kind startup, isolated database creation, and graceful shutdown."
} catch {
    foreach ($log in @($stdoutLog, $stderrLog, $applicationLog)) {
        if (Test-Path $log) { Get-Content $log -Tail 200 | Write-Host }
    }
    throw
} finally {
    try {
        if ($null -ne $app -and -not $app.HasExited) {
            Stop-Process -Id $app.Id -Force -ErrorAction SilentlyContinue
            if (-not $app.WaitForExit(5000)) { throw "Could not stop the application during cleanup" }
        }
        if ($installed) {
            $uninstall = Start-Process msiexec.exe -ArgumentList @("/x", "`"$artifactPath`"", "/qn", "/norestart") -PassThru -Wait
            if ($uninstall.ExitCode -ne 0) { throw "MSI uninstall returned $($uninstall.ExitCode)" }
        }
    } finally {
        Remove-Item -Path $work -Recurse -Force
    }
}
