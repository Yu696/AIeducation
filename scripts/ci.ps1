# ============================================================================
# scripts/ci.ps1 —— Sprint 2 本地等价 CI（Windows PowerShell）
#
# 用途：在提交/合入前执行与 GitHub Actions 等价的检查：
#   1. 后端 H2 集成测试（mvn test）
#   2. 前端 JS 语法检查（node --check）
#   3. 可选：端到端冒烟测试（需后端已启动在 localhost:8080）
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File scripts/ci.ps1
#   $env:SMOKE="1"; powershell -ExecutionPolicy Bypass -File scripts/ci.ps1
#
# 退出码非 0 表示检查失败，不得合入 main。
# ============================================================================

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "backend"

Write-Host "==> [1/3] Backend tests (H2)"
Push-Location $backend
try {
    # 优先离线执行；若本地仓库缺依赖，再联网重试
    mvn -o -B test
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Offline Maven failed, retrying online..."
        mvn -B test
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Backend tests failed"
    }
} finally {
    Pop-Location
}

Write-Host "==> [2/3] Frontend JS syntax"
$node = Get-Command node -ErrorAction SilentlyContinue
if ($null -eq $node) {
    Write-Warning "Node.js not found; skip frontend syntax check."
} else {
    Get-ChildItem (Join-Path $root "frontend/assets/js") -Filter "*.js" | ForEach-Object {
        & node --check $_.FullName
        if ($LASTEXITCODE -ne 0) { throw "JS syntax check failed: $($_.FullName)" }
    }
    Get-ChildItem (Join-Path $root "frontend/assets/js/pages") -Filter "*.js" | ForEach-Object {
        & node --check $_.FullName
        if ($LASTEXITCODE -ne 0) { throw "JS syntax check failed: $($_.FullName)" }
    }
}

if ($env:SMOKE -eq "1") {
    Write-Host "==> [3/3] Smoke test (backend must be running on :8080)"
    python (Join-Path $root "docs/smoke_test.py")
    if ($LASTEXITCODE -ne 0) {
        throw "Smoke test failed"
    }
} else {
    Write-Host "==> [3/3] Smoke test skipped (set SMOKE=1 and start backend to enable)."
}

Write-Host "CI equivalent passed."
