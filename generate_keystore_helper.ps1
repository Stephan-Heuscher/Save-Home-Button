
# PowerShell Script to Generate Android Keystore and GitHub Secrets
# Usage: .\generate_keystore_helper.ps1

$keystorePath = "C:\Users\steph\OneDrive\back_home_dot.jks"
$alias = "back_home_dot_key"

# 1. Check if keystore exists
if (Test-Path $keystorePath) {
    Write-Host "✅  Existing keystore found at $keystorePath" -ForegroundColor Green
    $password = Read-Host "Enter the password for this keystore (to verify and output secrets)"
} else {
    Write-Host "⚠️  No keystore found at $keystorePath" -ForegroundColor Yellow
    $create = Read-Host "Do you want to generate a NEW release keystore? (y/n)"
    if ($create -ne "y") { return }

    $password = -join ((33..126) | Get-Random -Count 16 | % {[char]$_})
    Write-Host "Generated secure password: $password" -ForegroundColor Cyan
    
    # Generate keystore using keytool (standard in JDK)
    Write-Host "Generating keystore..."
    $dname = "CN=SafeHomeButton, OU=Mobile, O=Heuscher, L=Zurich, S=Zurich, C=CH"
    & keytool -genkeypair -v -keystore $keystorePath -keyalg RSA -keysize 2048 -validity 10000 -alias $alias -storepass $password -keypass $password -dname $dname
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌  Failed to generate keystore. Ensure Java JDK is installed and 'keytool' is in your PATH." -ForegroundColor Red
        return
    }
    Write-Host "✅  Keystore generated successfully!" -ForegroundColor Green
}

# 2. Convert to Base64
try {
    $bytes = [IO.File]::ReadAllBytes($keystorePath)
    $base64 = [Convert]::ToBase64String($bytes)
} catch {
    Write-Host "❌  Error reading keystore file: $_" -ForegroundColor Red
    return
}

# 3. Output Secrets for GitHub
Write-Host "`n"
Write-Host "====================================================================================================" -ForegroundColor White
Write-Host "   GITHUB SECRETS CONFIGURATION" -ForegroundColor Cyan
Write-Host "====================================================================================================" -ForegroundColor White
Write-Host "Go to: https://github.com/StartIt/Save-Home-Button/settings/secrets/actions"
Write-Host "Add the following Repository Secrets:`n"

Write-Host "Secret Name: " -NoNewline; Write-Host "ANDROID_KEYSTORE" -ForegroundColor Yellow
Write-Host "Value (Copy the long string below):"
Write-Host "-----------------------------------"
Write-Host $base64
Write-Host "-----------------------------------`n"

Write-Host "Secret Name: " -NoNewline; Write-Host "KEYSTORE_PASSWORD" -ForegroundColor Yellow
Write-Host "Value: " -NoNewline; Write-Host $password -ForegroundColor Cyan
Write-Host "`n"

Write-Host "Secret Name: " -NoNewline; Write-Host "KEY_ALIAS" -ForegroundColor Yellow
Write-Host "Value: " -NoNewline; Write-Host $alias -ForegroundColor Cyan
Write-Host "====================================================================================================" -ForegroundColor White
