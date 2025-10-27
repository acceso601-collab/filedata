@echo off
if exist "%~dp0\gradle\wrapper\gradle-wrapper.jar" (
  java -jar "%~dp0\gradle\wrapper\gradle-wrapper.jar" %*
) else (
  where gradle >nul 2>nul
  if %errorlevel%==0 (
    gradle %*
  ) else (
    echo No gradle wrapper found and gradle not installed. Please install gradle or include wrapper jar.
    exit /b 1
  )
)