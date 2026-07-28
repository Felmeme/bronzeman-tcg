param(
    [Parameter(Mandatory = $true)][string] $TcgJar,
    [string] $Output
)

if (-not $Output) {
    $Output = Join-Path $PSScriptRoot '..\src\main\resources\equipment_slots.json'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $TcgJar))
try {
    $entry = $zip.GetEntry('Card.json')
    if (-not $entry) { throw 'Card.json is missing from the OSRS TCG jar.' }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { $cards = $reader.ReadToEnd() | ConvertFrom-Json }
    finally { $reader.Dispose() }
}
finally { $zip.Dispose() }

$slots = [ordered]@{}
foreach ($card in ($cards | Sort-Object name)) {
    if ($card.name -and $card.equipmentSlot) {
        $slots[$card.name.Trim().ToLowerInvariant()] = $card.equipmentSlot.Trim().ToLowerInvariant()
    }
}

$payload = [ordered]@{ itemSlots = $slots } | ConvertTo-Json -Depth 4
[IO.File]::WriteAllText((Join-Path (Resolve-Path (Split-Path $Output -Parent)) (Split-Path $Output -Leaf)), $payload + "`n", [Text.UTF8Encoding]::new($false))
Write-Output "Wrote $($slots.Count) equipment-slot mappings to $Output"
