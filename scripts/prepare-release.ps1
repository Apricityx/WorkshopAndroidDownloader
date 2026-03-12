$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

function Confirm-YesNo {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Prompt,
        [string]$Default = 'N'
    )

    $suffix = if ($Default -eq 'Y') { '[Y/n]' } else { '[y/N]' }

    while ($true) {
        $answer = Read-Host "$Prompt $suffix"
        if ([string]::IsNullOrWhiteSpace($answer)) {
            $answer = $Default
        }

        switch -Regex ($answer.Trim()) {
            '^(?i:y|yes)$' { return $true }
            '^(?i:n|no)$' { return $false }
            default { Write-Host 'Please enter y or n.' }
        }
    }
}

function Get-GradleVersionName {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^application\.version\.name\s*=(.*)$') {
            return $Matches[1].Trim()
        }
    }

    return $null
}

function New-ReleaseNoteTemplate {
    param(
        [Parameter(Mandatory = $true)]
        [string]$NoteFile,
        [Parameter(Mandatory = $true)]
        [string]$NoteFileRelative,
        [Parameter(Mandatory = $true)]
        [string]$TagName
    )

    if (Test-Path -LiteralPath $NoteFile) {
        Write-Host "Release note already exists, keeping current file: $NoteFileRelative"
        return
    }

    $noteDir = Split-Path -Parent $NoteFile
    New-Item -ItemType Directory -Force -Path $noteDir | Out-Null

    $content = @(
        "发布日期：$(Get-Date -Format 'yyyy-MM-dd')",
        '',
        '## 新特性',
        '- ',
        '',
        '## 修复',
        '- '
    )

    Set-Content -LiteralPath $NoteFile -Value $content -Encoding UTF8
    Write-Host "Created release note template: $NoteFileRelative"
}

function Invoke-Git {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git command failed: git $($Arguments -join ' ')"
    }
}

function Main {
    $scriptDir = $PSScriptRoot
    $repoRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptDir '..'))
    $didPushLocation = $false

    $null = & git -C $repoRoot rev-parse --show-toplevel 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw 'This script must be run inside a Git repository.'
    }

    try {
        Push-Location $repoRoot
        $didPushLocation = $true

        $gradleFile = Join-Path $repoRoot 'gradle.properties'
        if (-not (Test-Path -LiteralPath $gradleFile)) {
            throw "gradle.properties not found: $gradleFile"
        }

        $versionName = Get-GradleVersionName -Path $gradleFile
        if ([string]::IsNullOrWhiteSpace($versionName)) {
            throw 'Failed to read application.version.name from gradle.properties.'
        }

        $tagName = "v$versionName"
        $noteFileRelative = "docs/release/note/$tagName.md"
        $noteFile = Join-Path $repoRoot $noteFileRelative

        if (-not (Confirm-YesNo -Prompt "Release version ${tagName}?")) {
            Write-Host 'Release cancelled.'
            return
        }

        $currentBranch = (& git branch --show-current).Trim()
        if ([string]::IsNullOrWhiteSpace($currentBranch)) {
            throw 'Detached HEAD detected. Cannot push the current branch automatically.'
        }

        $upstreamRef = (& git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' 2>$null).Trim()
        $remoteName = 'origin'
        $remoteBranch = $currentBranch

        if (-not [string]::IsNullOrWhiteSpace($upstreamRef) -and $upstreamRef.Contains('/')) {
            $parts = $upstreamRef.Split('/', 2)
            $remoteName = $parts[0]
            $remoteBranch = $parts[1]
        }

        & git remote get-url $remoteName *> $null
        if ($LASTEXITCODE -ne 0) {
            throw "Remote not found: $remoteName"
        }

        & git rev-parse -q --verify "refs/tags/$tagName" *> $null
        if ($LASTEXITCODE -eq 0) {
            throw "Local tag already exists: $tagName"
        }

        New-ReleaseNoteTemplate -NoteFile $noteFile -NoteFileRelative $noteFileRelative -TagName $tagName

        Write-Host "Edit $noteFileRelative and fill in the release notes."
        $noteReady = $false
        while (-not $noteReady) {
            $ready = Read-Host 'Enter y to continue after editing, or n to cancel this release'
            switch -Regex ($ready.Trim()) {
                '^(?i:y|yes)$' {
                    $noteReady = $true
                }
                '^(?i:n|no)$' {
                    Write-Host 'Release cancelled.'
                    return
                }
                default { Write-Host 'Please enter y or n.' }
            }
        }

        $currentVersionName = Get-GradleVersionName -Path $gradleFile
        $currentTagName = "v$currentVersionName"
        if ($currentTagName -ne $tagName) {
            throw "gradle.properties version changed to $currentTagName. Re-run the script."
        }

        if (-not (Test-Path -LiteralPath $noteFile)) {
            throw "Release note file not found: $noteFileRelative"
        }

        $gradleDirty = -not [string]::IsNullOrWhiteSpace((& git status --porcelain -- 'gradle.properties'))

        Write-Host ''
        Write-Host 'About to release:'
        Write-Host "  Version: $tagName"
        Write-Host "  Release note: $noteFileRelative"
        if ($gradleDirty) {
            Write-Host '  Extra file: gradle.properties'
        } else {
            Write-Host '  Extra file: no local gradle.properties changes'
        }
        Write-Host "  Commit: chore(release): prepare $tagName"
        Write-Host "  Push target: ${remoteName}/${remoteBranch}"
        Write-Host ''

        if (-not (Confirm-YesNo -Prompt 'Commit and push this release?')) {
            Write-Host 'Release cancelled. No commit or tag was created.'
            return
        }

        $remoteTagOutput = (& git ls-remote --tags $remoteName "refs/tags/$tagName" 2>$null)
        if ($LASTEXITCODE -ne 0) {
            throw 'Failed to query the remote tag. Check network access and repository permissions.'
        }
        if (-not [string]::IsNullOrWhiteSpace(($remoteTagOutput | Out-String).Trim())) {
            throw "Remote tag already exists: $tagName"
        }

        Invoke-Git -Arguments @('add', '--', $noteFileRelative)
        $commitPaths = @($noteFileRelative)
        if ($gradleDirty) {
            Invoke-Git -Arguments @('add', '--', 'gradle.properties')
            $commitPaths += 'gradle.properties'
        }

        $diffArgs = @('diff', '--cached', '--quiet', '--') + $commitPaths
        & git @diffArgs
        if ($LASTEXITCODE -eq 0) {
            throw "No release changes to commit. Edit $noteFileRelative or update gradle.properties."
        }
        if ($LASTEXITCODE -gt 1) {
            throw 'git diff --cached failed.'
        }

        $commitMessage = "chore(release): prepare $tagName"
        $commitArgs = @('commit', '--only', '-m', $commitMessage, '--') + $commitPaths
        Invoke-Git -Arguments $commitArgs
        Invoke-Git -Arguments @('tag', '-a', $tagName, '-m', "Release $tagName")
        Invoke-Git -Arguments @('push', $remoteName, "HEAD:$remoteBranch")
        Invoke-Git -Arguments @('push', $remoteName, "refs/tags/$tagName")

        Write-Host ''
        Write-Host 'Release preparation completed:'
        Write-Host "  Commit: $commitMessage"
        Write-Host "  Tag: $tagName"
        Write-Host "  Pushed to: ${remoteName}/${remoteBranch}"
    }
    finally {
        if ($didPushLocation) {
            Pop-Location
        }
    }
}

try {
    Main
}
catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 1
}
