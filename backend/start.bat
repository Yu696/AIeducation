@echo off
REM ============================================================================
REM 爱教学 Sprint 1 启动脚本（Windows）
REM 作用：先用 Maven 导出运行时依赖清单，再以 classpath 方式启动 Spring Boot 应用。
REM 说明：本机 Maven 仓库处于离线状态，maven-jar-plugin 缺少依赖无法打可执行 jar，
REM       因此这里用 classpath 启动；网络正常的环境可直接改用 mvn spring-boot:run。
REM ============================================================================
setlocal
cd /d "%~dp0"

if not exist "target\cp.txt" (
    echo [1/2] 导出运行时依赖清单...
    call mvn -o -B -q org.apache.maven.plugins:maven-dependency-plugin:3.9.0:build-classpath -Dmdep.outputFile=target\cp.txt -Dmdep.includeScope=runtime
    if errorlevel 1 (
        echo [错误] 依赖导出失败，请确认已执行过 mvn compile
        exit /b 1
    )
)

echo [2/2] 启动后端（MySQL: localhost:3306/aijiaoxue, 端口 8080）...
set CP=target\classes
for /f "usebackq delims=" %%i in ("target\cp.txt") do set CP=%CP%;%%i
java -Dfile.encoding=UTF-8 -cp "%CP%" com.aijiaoxue.AijiaoxueApplication
endlocal
