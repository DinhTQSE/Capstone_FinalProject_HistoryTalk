<#
install-maven.ps1

PowerShell helper to install Apache Maven on Windows.
Options:
 - If Chocolatey is available: installs via `choco install maven -y`.
 - Otherwise: downloads specified Maven binary zip, extracts to "C:\Program Files\Apache\maven-<ver>", and sets user environment variables.

Run as Administrator to install into Program Files and persist PATH changes.
#>

param(
    [string]$MavenVersion = "3.9.6",
    [switch]$UseChocolatey
)

function Exec([string]$cmd) { Write-Host "> $cmd"; iex $cmd }

Write-Host "Checking Java..."
try { & java -version 2>$null } catch { Write-Host "Java not found. Please install Java 17+ and set JAVA_HOME first."; exit 1 }

if ($UseChocolatey -or (Get-Command choco -ErrorAction SilentlyContinue)) {
    Write-Host "Installing Maven via Chocolatey..."
    Exec "choco install maven -y"
    Write-Host "Done. Open a new PowerShell to pick up PATH changes, then run: mvn -v"
    exit 0
}

$mavenFile = "apache-maven-$MavenVersion-bin.zip"
$downloadUrl = "https://archive.apache.org/dist/maven/maven-3/$MavenVersion/binaries/$mavenFile"
$tmp = Join-Path $env:TEMP $mavenFile

Write-Host "Downloading Apache Maven $MavenVersion from $downloadUrl"
try {
    Invoke-WebRequest -Uri $downloadUrl -OutFile $tmp -UseBasicParsing -ErrorAction Stop
} catch {
    Write-Host "Download failed. Check the version and your network, then try again."
    exit 1
}

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if ($isAdmin) {
    $installDir = "C:\Program Files\Apache\maven-$MavenVersion"
} else {
    $installDir = Join-Path $env:LOCALAPPDATA "Apache\maven-$MavenVersion"
}
if (-not (Test-Path $installDir)) { New-Item -ItemType Directory -Path $installDir -Force | Out-Null }

Write-Host "Extracting to $installDir"
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (-not (Test-Path $tmp)) {
    Write-Host "Zip file not found: $tmp"
    exit 1
}

[System.IO.Compression.ZipFile]::ExtractToDirectory($tmp, $installDir)

# The zip contains a top-level folder apache-maven-<ver>, move its contents up
$extracted = Join-Path $installDir "apache-maven-$MavenVersion"
if (Test-Path $extracted) {
    Get-ChildItem -Path $extracted | Move-Item -Destination $installDir -Force
    Remove-Item -Recurse -Force $extracted
}

$mavenHome = $installDir

Write-Host "Setting environment variables (user scope)..."
setx M2_HOME "$mavenHome" | Out-Null
setx MAVEN_HOME "$mavenHome" | Out-Null

# Append Maven bin to PATH (user)
$currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($currentPath -notlike "*${mavenHome}\bin*") {
    $newPath = "$currentPath;$mavenHome\bin"
    setx Path $newPath | Out-Null
}

Write-Host "Maven $MavenVersion installed to $mavenHome"
Write-Host "Please open a NEW PowerShell window and run: mvn -v"