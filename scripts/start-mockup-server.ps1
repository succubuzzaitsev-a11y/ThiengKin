Set-Location D:\thiengKin\docs
Start-Process -FilePath python -ArgumentList '-m','http.server','8765' -WindowStyle Hidden
Start-Sleep -Seconds 3
$ok = Test-NetConnection -ComputerName localhost -Port 8765 -InformationLevel Quiet
Write-Host "Server up: $ok"
