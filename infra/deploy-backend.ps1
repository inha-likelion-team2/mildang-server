# =====================================================================
# 백엔드 배포 — 로컬(Windows)에서 실행
# 빌드는 로컬에서 하고 jar만 EC2에 올린다 (t2.micro에서 빌드 금지 — 메모리 부족)
# 사용법: .\infra\deploy-backend.ps1 -EC2Host <IP또는도메인> -PemPath C:\path\to\key.pem
# =====================================================================
param(
    [Parameter(Mandatory = $true)][string]$EC2Host,
    [Parameter(Mandatory = $true)][string]$PemPath
)
$ErrorActionPreference = "Stop"

Write-Host "== 1. bootJar 빌드 =="
.\gradlew.bat bootJar --console=plain
$jar = Get-ChildItem "build\libs\*.jar" | Where-Object { $_.Name -notlike "*plain*" } | Select-Object -First 1
if (-not $jar) { throw "jar를 찾지 못했습니다" }
Write-Host "jar: $($jar.Name)"

Write-Host "== 2. 업로드 =="
scp -i $PemPath -o StrictHostKeyChecking=accept-new $jar.FullName "ubuntu@${EC2Host}:/tmp/mildang-server.jar"

Write-Host "== 3. 교체 + 재시작 =="
ssh -i $PemPath "ubuntu@${EC2Host}" "sudo mv /tmp/mildang-server.jar /opt/mildang/mildang-server.jar && sudo systemctl restart mildang-server && sleep 8 && systemctl is-active mildang-server"

Write-Host "== 4. 헬스체크 =="
ssh -i $PemPath "ubuntu@${EC2Host}" "curl -s http://localhost:8080/v1/health"
Write-Host ""
Write-Host "배포 완료"
