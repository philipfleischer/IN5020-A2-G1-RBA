@echo off
rem
rem Builds the two ready-to-deploy jar files:
rem   jars\MessageDeliveryServer.jar
rem   jars\BankServer.jar
rem
rem Usage (Windows), from the project folder:
rem   scripts\build.bat
rem
rem Needs a JDK 11 or newer (javac and jar on the PATH).

rem Run from the project folder, wherever the script is called from
cd /d "%~dp0\.."

if exist out rmdir /s /q out
if exist jars rmdir /s /q jars
mkdir out
mkdir jars

rem Windows has no "find", so we list the source files in a file
rem and give that file to javac with @
dir /s /b src\*.java > "%TEMP%\in5020_sources.txt"

rem --release 11: the jars run on any Java 11 or newer
javac --release 11 -d out @"%TEMP%\in5020_sources.txt" || exit /b 1

jar --create --file jars\MessageDeliveryServer.jar --main-class src.message_delivery_server.MessageDeliveryServer -C out . || exit /b 1
jar --create --file jars\BankServer.jar --main-class src.bank_server.BankServer -C out . || exit /b 1

echo Built jars\MessageDeliveryServer.jar and jars\BankServer.jar
