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
