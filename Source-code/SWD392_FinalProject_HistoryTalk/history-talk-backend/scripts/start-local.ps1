# PowerShell helper: load secretKey.properties and AI .env, then start Java and AI services in new windows
# Usage: Right-click -> Run with PowerShell, or run from an elevated PowerShell prompt.

function Load-EnvFile($path) {
    $result = @{}
    if (Test-Path $path) {
        Get-Content $path | ForEach-Object {
            $_ = $_.Trim()
            if (-not [string]::IsNullOrWhiteSpace($_) -and -not $_.StartsWith("#")) {
                $parts = $_ -split "=",2
                if ($parts.Length -eq 2) {
                    $k = $parts[0].Trim()
                    $v = $parts[1].Trim()
                    $result[$k] = $v
                }
            }
        }
    }
    return $result
}

$repoRoot = "c:\Users\KHAI\Documents\Historical-talk\SWD392_FinalProject_HistoryTalk\Source-code\SWD392_FinalProject_HistoryTalk"
$javaDir = Join-Path $repoRoot "history-talk-backend"
$aiDir = Join-Path $repoRoot "history-talk-backend-AI"

# Load Java secretKey.properties
$javaEnv = Load-EnvFile (Join-Path $javaDir "src\main\resources\secretKey.properties")
foreach ($k in $javaEnv.Keys) { $env:$k = $javaEnv[$k] }

# Load AI .env
$aiEnv = Load-EnvFile (Join-Path $aiDir ".env")
foreach ($k in $aiEnv.Keys) { $env:$k = $aiEnv[$k] }

Write-Host "Environment variables loaded into current session (not persisted)."
Write-Host "Starting Java backend in new PowerShell window..."
Start-Process -FilePath "powershell" -ArgumentList "-NoExit","-Command","Set-Location -Path '$javaDir'; mvn spring-boot:run" -WorkingDirectory $javaDir

Start-Sleep -Seconds 2
Write-Host "Starting AI service in new PowerShell window..."
Start-Process -FilePath "powershell" -ArgumentList "-NoExit","-Command","Set-Location -Path '$aiDir'; if (Test-Path '.venv\Scripts\Activate') { . '.venv\Scripts\Activate'; } ; python main.py" -WorkingDirectory $aiDir

Write-Host "Launched both processes. Check the new windows for logs."