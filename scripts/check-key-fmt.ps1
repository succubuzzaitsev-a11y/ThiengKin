$key = 'fsq38SVYBd0cVlZePSWdnTL/QeF/YQhZE7odoQc1k2mv2KE='
Write-Host "Length: $($key.Length)"
Write-Host "Prefix: $($key.Substring(0,5))"
Write-Host "Suffix: $($key.Substring($key.Length-3))"
Write-Host "Full:   $key"
