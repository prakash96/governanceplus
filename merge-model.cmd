@echo off
setlocal

set "MODEL_NAME=Llama-3.2-3B-Instruct-Q4_K_M.gguf"
set "OUTPUT=models\%MODEL_NAME%"
set "CHUNKS=models\chunks"

if exist "%OUTPUT%" (
    echo Removing existing %OUTPUT%...
    del "%OUTPUT%"
)

echo Merging chunks from %CHUNKS%\...

for /f "tokens=*" %%f in ('dir /b /on "%CHUNKS%\*.zip"') do (
    echo   %%f
    tar -xOf "%CHUNKS%\%%f" >> "%OUTPUT%"
)

echo.
echo Done. Model restored to %OUTPUT%
