$ErrorActionPreference = 'Stop'

$repository = 'Shanks914/PC-Hardware-Analyzer'
$releaseApi = "https://api.github.com/repos/$repository/releases/latest"
$release = Invoke-RestMethod -Uri $releaseApi -Headers @{ 'User-Agent' = 'PC-Hardware-Analyzer-Launcher' }
$asset = $release.assets | Where-Object { $_.name -match '-portable\.zip$' } | Select-Object -First 1

if (-not $asset) {
    throw "No portable Windows package is attached to the latest release ($($release.tag_name))."
}

$installRoot = Join-Path $env:LOCALAPPDATA 'PC Hardware Analyzer\Portable'
$appDirectory = Join-Path $installRoot $release.tag_name
$appImageDirectory = Join-Path $appDirectory 'PC Hardware Analyzer'
$appPath = Join-Path $appImageDirectory 'PC Hardware Analyzer.exe'

if (-not (Test-Path -LiteralPath $appPath)) {
    New-Item -ItemType Directory -Path $appDirectory -Force | Out-Null
    $archivePath = Join-Path $appDirectory $asset.name
    Write-Host "Downloading the portable app from GitHub ($($release.tag_name))..."
    Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $archivePath
    Expand-Archive -LiteralPath $archivePath -DestinationPath $appDirectory -Force
    Remove-Item -LiteralPath $archivePath -Force
}

if (-not (Test-Path -LiteralPath $appPath)) {
    throw "The portable archive did not contain the expected application: $appPath"
}

Start-Process -FilePath $appPath
