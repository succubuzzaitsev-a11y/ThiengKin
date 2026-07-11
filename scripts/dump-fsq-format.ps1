$key = "YXKDFN2YWJWBKMEBZB10IQ5SBL4GTSR0MO0SQSPYSNLY3YDD"
$h = @{
    "Authorization" = "Bearer $key"
    "X-Places-Api-Version" = "2025-06-17"
    "Accept" = "application/json"
}
$r = Invoke-RestMethod -Uri "https://places-api.foursquare.com/places/search?ll=18.7883,98.9853&limit=1" -Headers $h
$r.results[0] | ConvertTo-Json -Depth 5
