# Deterministic process snapshots; no real processes are closed and no sleeps are used.
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "windows-smoke-processes.ps1")

$snapshot = @(
    [pscustomobject] @{ ProcessId = 30; ParentProcessId = 20 },
    [pscustomobject] @{ ProcessId = 99; ParentProcessId = 1 },
    [pscustomobject] @{ ProcessId = 20; ParentProcessId = 10 },
    [pscustomobject] @{ ProcessId = 10; ParentProcessId = 1 }
)
$actual = @(Get-SmokeProcessIds -RootProcessId 10 -Snapshot $snapshot)
if (($actual -join ',') -ne '10,20,30') { throw "Must discover only the launched process tree, including nested children" }
$actual = @(Get-SmokeProcessIds -RootProcessId 50 -Snapshot @())
if (($actual -join ',') -ne '50') { throw "A childless executable must still be closed" }

function New-FakeSmokeProcess {
    param([bool] $Exited, [int] $Window)
    $process = [pscustomobject] @{ HasExited = $Exited; MainWindowHandle = $Window; Closed = 0; Refreshed = 0 }
    $process | Add-Member ScriptMethod Refresh { $this.Refreshed++ }
    $process | Add-Member ScriptMethod CloseMainWindow { $this.Closed++; return $true }
    return $process
}
$parent = New-FakeSmokeProcess $false 0
$child = New-FakeSmokeProcess $false 42
$exited = New-FakeSmokeProcess $true 43
Close-SmokeWindows -Processes @($parent, $child, $exited)
if ($parent.Closed -ne 0 -or $child.Closed -ne 1 -or $exited.Closed -ne 0) {
    throw "Only a live window owner should receive a graceful close request"
}
if ($parent.Refreshed -ne 1 -or $child.Refreshed -ne 1 -or $exited.Refreshed -ne 1) {
    throw "Cached process/window state must be refreshed before deciding"
}
$failure = New-FakeSmokeProcess $false 1
$failure | Add-Member ScriptMethod CloseMainWindow { throw "synthetic close failure" } -Force
$failed = $false
try { Close-SmokeWindows -Processes @($failure) } catch { $failed = $true }
if (-not $failed) { throw "A close failure must not be converted into success" }
Write-Host "Windows smoke process-tree and graceful-close regression checks passed."
