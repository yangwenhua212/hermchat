param(
    [Parameter(Mandatory = $true)]
    [string]$Endpoint,
    [string]$Kind = "",
    [string]$Name = "",
    [switch]$NoOpen
)

$inferred = "WEBSOCKET"
$lower = $Endpoint.ToLowerInvariant()
if ($lower.StartsWith("http://") -or $lower.StartsWith("https://")) {
    $inferred = "HTTP_COMPAT"
}
if ([string]::IsNullOrWhiteSpace($Kind)) {
    $Kind = $inferred
}

$payload = [ordered]@{
    v        = 1
    kind     = $Kind.ToUpperInvariant()
    endpoint = $Endpoint.Trim()
}
if (-not [string]::IsNullOrWhiteSpace($Name)) {
    $payload.name = $Name
}

$json = ($payload | ConvertTo-Json -Compress)
Write-Output $json

if (-not $NoOpen) {
    $encoded = [uri]::EscapeDataString($json)
    $url = "https://api.qrserver.com/v1/create-qr-code/?size=280x280&data=$encoded"
    Write-Host "Opening $url"
    Start-Process $url
}
