@echo off
set "JAVA_HOME=C:\Users\admin\.jdks\ms-17.0.19"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "GRADLE_USER_HOME=%~dp0.gradle-user-home"
set "GRADLE_DIST_ROOT=C:\Users\admin\.gradle\wrapper\dists\gradle-8.7-bin"
set "LOCAL_GRADLE="

for /d %%D in ("%GRADLE_DIST_ROOT%\*") do (
    if exist "%%D\gradle-8.7\bin\gradle.bat" set "LOCAL_GRADLE=%%D\gradle-8.7\bin\gradle.bat"
)

if defined LOCAL_GRADLE (
    call "%LOCAL_GRADLE%" %*
) else (
    call "%~dp0gradlew.bat" %*
)
