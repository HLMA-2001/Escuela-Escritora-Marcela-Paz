param(
    [Parameter(Mandatory)][ValidateSet('DOCENTE','INSPECTOR','SIN_ROL')][string]$Rol,
    [string]$DirectorioDatos = './datos'
)
$ErrorActionPreference = 'Stop'
# Pide la credencial en privado. El archivo del servidor guarda solamente su huella.
$entrada = Read-Host 'Credencial de la cuenta' -AsSecureString
$token = [Guid]::Parse([Net.NetworkCredential]::new('', $entrada).Password)
$sha = [Security.Cryptography.SHA256]::Create()
try {
    $bytes = $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($token.ToString()))
} finally { $sha.Dispose() }
$huella = [BitConverter]::ToString($bytes).Replace('-','').ToLowerInvariant()
$dir = (New-Item -ItemType Directory -Force -Path $DirectorioDatos).FullName
$destino = Join-Path $dir 'roles.json'
$roles = @()
if (Test-Path -LiteralPath $destino) {
    $guardados = Get-Content -LiteralPath $destino -Raw | ConvertFrom-Json
    $roles = @(foreach ($permiso in $guardados) {
        if ($permiso.huella -ne $huella) {
            @{ huella = [string]$permiso.huella; rol = [string]$permiso.rol }
        }
    })
}
$roles += @{huella=$huella; rol=$Rol}
$temporal = Join-Path $dir ("roles-" + [Guid]::NewGuid() + '.tmp')
$respaldo = "$temporal.bak"
try {
    [IO.File]::WriteAllText($temporal, (ConvertTo-Json -InputObject $roles -Depth 4))
    if ([IO.File]::Exists($destino)) { [IO.File]::Replace($temporal, $destino, $respaldo) }
    else { [IO.File]::Move($temporal, $destino) }
} finally {
    if ([IO.File]::Exists($temporal)) { [IO.File]::Delete($temporal) }
    if ([IO.File]::Exists($respaldo)) { [IO.File]::Delete($respaldo) }
}
"Rol aplicado: $Rol"


