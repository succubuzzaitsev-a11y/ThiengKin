$env:FOURSQUARE_API_KEY = "YXKDFN2YWJWBKMEBZB10IQ5SBL4GTSR0MO0SQSPYSNLY3YDD"
Set-Location D:\thiengKin\scripts
Write-Host "=== SETUP ===" -ForegroundColor Cyan
node setup-chiangmai.mjs
if ($LASTEXITCODE -ne 0) { Write-Host "Setup failed" -ForegroundColor Red; exit 1 }

# Move output to data/
if (Test-Path "chiangmai-restaurants.json") {
    Move-Item "chiangmai-restaurants.json" "D:\thiengKin\data\chiangmai-restaurants.json" -Force
    Write-Host "`n=== Moved chiangmai-restaurants.json -> data/ ===" -ForegroundColor Green
}

Write-Host "`n=== FILTER ===" -ForegroundColor Cyan
node filter-data.mjs
if ($LASTEXITCODE -ne 0) { Write-Host "Filter failed" -ForegroundColor Red; exit 1 }

Write-Host "`n=== MERGE ===" -ForegroundColor Cyan
node merge-data.mjs
if ($LASTEXITCODE -ne 0) { Write-Host "Merge failed" -ForegroundColor Red; exit 1 }

Write-Host "`n=== ALL DONE ===" -ForegroundColor Green
