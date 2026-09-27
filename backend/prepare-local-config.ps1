$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '.env'
$targetPath = Join-Path $PSScriptRoot '.local.env'
if (-not (Test-Path -LiteralPath $sourcePath)) {
    throw 'backend/.env non esiste. Crea il file con le credenziali locali prima di proseguire.'
}

$values = [ordered]@{}
foreach ($line in [System.IO.File]::ReadAllLines($sourcePath)) {
    if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') { continue }
    $name = $Matches[1]
    $value = $Matches[2].Trim()
    if ($value.Length -ge 2 -and
        (($value.StartsWith('"') -and $value.EndsWith('"')) -or
         ($value.StartsWith("'") -and $value.EndsWith("'")))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    $values[$name] = $value
}

$required = @('DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET_KEY', 'JWT_EXPIRATION_TIME',
              'MAIL_HOST', 'MAIL_PORT', 'MAIL_USERNAME', 'MAIL_PASSWORD', 'MAIL_FROM')
$missing = @($required | Where-Object { -not $values.Contains($_) -or [string]::IsNullOrWhiteSpace($values[$_]) })
if ($missing.Count -gt 0) {
    throw "Valori mancanti in backend/.env: $($missing -join ', ')"
}

function New-RandomKey {
    $bytes = New-Object byte[] 32
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return [Convert]::ToBase64String($bytes)
}

$newLines = New-Object 'System.Collections.Generic.List[string]'
foreach ($name in @('PII_ENCRYPTION_KEY', 'PII_LOOKUP_KEY')) {
    if (-not $values.Contains($name) -or [string]::IsNullOrWhiteSpace($values[$name])) {
        $values[$name] = New-RandomKey
        $newLines.Add(('{0}="{1}"' -f $name, $values[$name]))
    }
}
if ($values['PII_ENCRYPTION_KEY'] -eq $values['PII_LOOKUP_KEY']) {
    throw 'PII_ENCRYPTION_KEY e PII_LOOKUP_KEY devono essere diverse.'
}
if (-not $values.Contains('FRONTEND_BASE_URL') -or [string]::IsNullOrWhiteSpace($values['FRONTEND_BASE_URL'])) {
    $values['FRONTEND_BASE_URL'] = 'http://localhost:5173'
    $newLines.Add('FRONTEND_BASE_URL="http://localhost:5173"')
}

$utf8 = [System.Text.UTF8Encoding]::new($false)
if ($newLines.Count -gt 0) {
    [System.IO.File]::AppendAllText($sourcePath,
        [Environment]::NewLine + ($newLines -join [Environment]::NewLine) + [Environment]::NewLine, $utf8)
}

$propertyLines = New-Object 'System.Collections.Generic.List[string]'
$propertyLines.Add('# Generated from .env. Do not commit or edit this file.')
foreach ($entry in $values.GetEnumerator()) {
    $escapedValue = $entry.Value.Replace('\', '\\').Replace("`r", '\r').Replace("`n", '\n')
    $propertyLines.Add(('{0}={1}' -f $entry.Key, $escapedValue))
}
[System.IO.File]::WriteAllLines($targetPath, $propertyLines, $utf8)
Write-Output 'Configurazione locale pronta. Prima di riavviare su un database esistente, segui PERSONAL_DATA_MIGRATION.md.'
