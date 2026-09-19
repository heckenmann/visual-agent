@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  gradlew startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables, and ensure extensions are enabled
setlocal EnableExtensions

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

"%COMSPEC%" /c exit 1

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

"%COMSPEC%" /c exit 1

:execute
set WRAPPER_JAR=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
if exist "%WRAPPER_JAR%" goto wrapperReady

for /f "tokens=2 delims=-" %%A in ('findstr /R "^distributionUrl=.*gradle-[0-9][0-9.]*-" "%APP_HOME%\gradle\wrapper\gradle-wrapper.properties"') do set WRAPPER_VERSION=%%A
if not defined WRAPPER_VERSION goto wrapperVersionMissing

@rem The project intentionally excludes binary JARs from version control.
@rem Download the tagged Gradle source JAR and verify it with Gradle's published checksum.
set WRAPPER_DOWNLOAD_URL=https://raw.githubusercontent.com/gradle/gradle/v%WRAPPER_VERSION%/gradle/wrapper/gradle-wrapper.jar
set WRAPPER_CHECKSUM_URL=https://services.gradle.org/distributions/gradle-%WRAPPER_VERSION%-wrapper.jar.sha256
set WRAPPER_DOWNLOAD_PATH=%WRAPPER_JAR%.download
powershell -NoProfile -Command "$ErrorActionPreference = 'Stop'; Invoke-WebRequest -Uri $env:WRAPPER_DOWNLOAD_URL -OutFile $env:WRAPPER_DOWNLOAD_PATH; $expected = ((Invoke-WebRequest -Uri $env:WRAPPER_CHECKSUM_URL).Content).Trim().ToLower(); $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $env:WRAPPER_DOWNLOAD_PATH).Hash.ToLower(); if ($actual -ne $expected) { throw 'Downloaded Gradle Wrapper checksum does not match the official checksum.' }"
if errorlevel 1 goto wrapperDownloadFailed

move /Y "%WRAPPER_DOWNLOAD_PATH%" "%WRAPPER_JAR%" >NUL
if errorlevel 1 goto wrapperDownloadFailed
goto wrapperReady

:wrapperVersionMissing
echo. 1>&2
echo ERROR: Unable to determine the Gradle Wrapper version. 1>&2
"%COMSPEC%" /c exit 1

:wrapperDownloadFailed
if exist "%WRAPPER_DOWNLOAD_PATH%" del "%WRAPPER_DOWNLOAD_PATH%"
echo. 1>&2
echo ERROR: Unable to download and verify Gradle Wrapper %WRAPPER_VERSION%. 1>&2
"%COMSPEC%" /c exit 1

:wrapperReady
@rem Setup the command line



@rem Execute gradlew
@rem endlocal doesn't take effect until after the line is parsed and variables are expanded
@rem which allows us to clear the local environment before executing the java command
endlocal & "%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -jar "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" %* & call :exitWithErrorLevel

:exitWithErrorLevel
@rem Use "%COMSPEC%" /c exit to allow operators to work properly in scripts
"%COMSPEC%" /c exit %ERRORLEVEL%
