# Native jpackage launchers can restart into a child process owning the UI.
# Scope all close requests to descendants of the process launched by this test.
function Get-SmokeProcessIds {
    param(
        [int] $RootProcessId,
        [object[]] $Snapshot
    )
    $owned = [System.Collections.Generic.HashSet[int]]::new()
    $null = $owned.Add($RootProcessId)
    do {
        $changed = $false
        foreach ($process in $Snapshot) {
            if ($owned.Contains([int] $process.ParentProcessId)) {
                if ($owned.Add([int] $process.ProcessId)) { $changed = $true }
            }
        }
    } while ($changed)
    return @($owned | Sort-Object)
}

function Close-SmokeWindows {
    param([object[]] $Processes)
    foreach ($process in $Processes) {
        $process.Refresh()
        if (-not $process.HasExited -and $process.MainWindowHandle -ne 0) {
            $null = $process.CloseMainWindow()
        }
    }
}

# Windows may keep a recently loaded native library locked briefly after its process exits.
# Retry cleanup for that transient lock, but still fail the smoke test if the workspace remains.
function Remove-SmokeWorkDirectory {
    param([string] $Path)

    $attemptLimit = 15
    for ($attempt = 1; $attempt -le $attemptLimit; $attempt++) {
        try {
            Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction Stop
            return
        } catch {
            if ($attempt -eq $attemptLimit) { throw }
            Write-Host "Smoke workspace cleanup is waiting for locked files (attempt $attempt of $attemptLimit)."
            Start-Sleep -Milliseconds 1000
        }
    }
}
