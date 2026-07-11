$key = "YXKDFN2YWJWBKMEBZB10IQ5SBL4GTSR0MO0SQSPYSNLY3YDD"

$tests = @(
    @{ name="v3-Bearer"; url="https://places-api.foursquare.com/places/search?ll=18.7883,98.9853&limit=1"; auth="Bearer" },
    @{ name="v3-Raw"; url="https://places-api.foursquare.com/places/search?ll=18.7883,98.9853&limit=1"; auth="Raw" },
    @{ name="v2-oauth_token"; url="https://api.foursquare.com/v2/venues/search?ll=18.7883,98.9853&limit=1&v=20250101"; auth="oauth" },
    @{ name="v2-key"; url="https://api.foursquare.com/v2/venues/search?ll=18.7883,98.9853&limit=1&v=20250101"; auth="key" }
)

foreach ($t in $tests) {
    Write-Host ""
    Write-Host "=== $($t.name) ==="
    $finalUrl = $t.url
    $h = @{ "X-Places-Api-Version" = "2025-06-17"; "Accept" = "application/json" }
    switch ($t.auth) {
        "Bearer" { $h["Authorization"] = "Bearer $key" }
        "Raw" { $h["Authorization"] = $key }
        "oauth" { $finalUrl = "$($t.url)&oauth_token=$key" }
        "key" { $finalUrl = "$($t.url)&key=$key" }
    }
    try {
        $r = Invoke-WebRequest -Uri $finalUrl -Headers $h -Method Get -UseBasicParsing
        Write-Host "  Status: $($r.StatusCode)"
        $body = if ($r.Content.Length -gt 300) { $r.Content.Substring(0, 300) + "..." } else { $r.Content }
        Write-Host "  Body: $body"
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        Write-Host "  Status: $code"
        try {
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $body = $reader.ReadToEnd()
            Write-Host "  Body: $body"
        } catch {
            Write-Host "  Body: (empty)"
        }
    }
}
