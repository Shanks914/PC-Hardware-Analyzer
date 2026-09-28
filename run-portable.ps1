$ErrorActionPreference = 'Stop'

$repository = 'Shanks914/PC-Hardware-Benchmark-Build-Analyzer'
$releaseDownloads = "https://github.com/$repository/releases/latest/download"
$versionUrl = "$releaseDownloads/PC-Hardware-Analyzer-version.txt"
$portableUrl = "$releaseDownloads/PC-Hardware-Analyzer-portable.zip"
$releaseVersion = (Invoke-WebRequest -Uri $versionUrl -UseBasicParsing).Content.Trim()

if ($releaseVersion -notmatch '^\d+\.\d+\.\d+$') {
    throw "GitHub returned an invalid portable app version: '$releaseVersion'."
}

$installRoot = Join-Path $env:LOCALAPPDATA 'PC Hardware Analyzer\Portable'
$appDirectory = Join-Path $installRoot "v$releaseVersion"
$appImageDirectory = Join-Path $appDirectory 'PC Hardware Analyzer'
$appPath = Join-Path $appImageDirectory 'PC Hardware Analyzer.exe'

if (-not (Test-Path -LiteralPath $appPath)) {
    New-Item -ItemType Directory -Path $appDirectory -Force | Out-Null
    $archivePath = Join-Path $appDirectory 'PC-Hardware-Analyzer-portable.zip'
    Write-Host "Downloading the portable app from GitHub (v$releaseVersion)..."
    Invoke-WebRequest -Uri $portableUrl -OutFile $archivePath
    Expand-Archive -LiteralPath $archivePath -DestinationPath $appDirectory -Force
    Remove-Item -LiteralPath $archivePath -Force
}

if (-not (Test-Path -LiteralPath $appPath)) {
    throw "The portable archive did not contain the expected application: $appPath"
}

Start-Process -FilePath $appPath
