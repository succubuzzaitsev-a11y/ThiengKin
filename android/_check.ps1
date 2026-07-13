$env:JAVA_HOME = "C:\Users\Succubuz\AppData\Local\Android\Sdk\jdk\17.0.8"
$env:Path = "C:\Users\Succubuz\AppData\Local\Android\Sdk\jdk\17.0.8\bin;" + $env:Path
Set-Location D:\thiengKin\android
./gradlew :app:compileDebugKotlin 2>&1 | Select-Object -Last 40
