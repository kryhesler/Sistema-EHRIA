@echo off
rem Probar la estacion EHRIA sin Arduino, sin cable OTG y sin Android Studio.
rem Solo hace falta un telefono Android con la depuracion USB activada y un cable USB normal.
rem La primera vez descarga adb (de Google) y la app de prueba en esta misma carpeta.
rem Uso: ehria.bat <orden> [valor]     (sin argumentos muestra la ayuda)
setlocal
set "APP=com.example.detectormovimiento"
set "ACCION=com.example.detectormovimiento.SIMULAR_ARDUINO"
set "AQUI=%~dp0"
set "URL_APK=https://raw.githubusercontent.com/kryhesler/Sistema-EHRIA/asistente-voz-punto-encuentro/herramientas/ehria-debug.apk"
set "URL_ADB=https://dl.google.com/android/repository/platform-tools-latest-windows.zip"

if "%~1"=="" goto ayuda

rem ---------- Buscar adb (o descargarlo) ----------
set "ADB=adb"
where adb >nul 2>nul && goto adbListo
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if exist "%ADB%" goto adbListo
set "ADB=%AQUI%platform-tools\adb.exe"
if exist "%ADB%" goto adbListo
echo Descargando adb de Google (unos 7 MB)...
if exist "%AQUI%platform-tools.zip" del "%AQUI%platform-tools.zip"
call :descargar "%URL_ADB%" "%AQUI%platform-tools.zip" || (echo No se pudo descargar adb. Revisa la conexion a internet. & goto fin)
rem Se descomprime desde la misma carpeta (una ruta que termina en \ antes de la comilla
rem confunde a tar). Si tar no esta o falla, se usa PowerShell.
pushd "%AQUI%"
"%SystemRoot%\System32\tar.exe" -xf platform-tools.zip 2>nul || powershell -NoProfile -Command "Expand-Archive -Force -Path 'platform-tools.zip' -DestinationPath '.'"
popd
if not exist "%ADB%" (echo No se pudo descomprimir adb. Descomprime a mano platform-tools.zip en esta carpeta y vuelve a intentar. & goto fin)
del "%AQUI%platform-tools.zip"
:adbListo
set "ENVIAR="%ADB%" shell am broadcast -a %ACCION% -p %APP%"

if /i "%~1"=="instalar"    goto instalar
if /i "%~1"=="reinstalar"  goto reinstalar
if /i "%~1"=="abrir"       goto abrir
if /i "%~1"=="telefono"    ("%ADB%" devices & goto fin)
if /i "%~1"=="conectar"    (%ENVIAR% --ez conectado true & goto fin)
if /i "%~1"=="desconectar" (%ENVIAR% --ez conectado false & goto fin)
if /i "%~1"=="cerca"       (%ENVIAR% --es ambiente 27.5,65,80 & goto fin)
if /i "%~1"=="lejos"       (%ENVIAR% --es ambiente 27.5,65,-1 & goto fin)
if /i "%~1"=="calor"       (%ENVIAR% --es ambiente 34,75,80 & goto fin)
if /i "%~1"=="ambiente"    (%ENVIAR% --es ambiente %~2 & goto fin)
if /i "%~1"=="decir"       ("%ADB%" shell "am broadcast -a %ACCION% -p %APP% --es decir '%~2'" & goto fin)
if /i "%~1"=="expresion"   (%ENVIAR% --es expresion %~2 & goto fin)
if /i "%~1"=="sismo"       (%ENVIAR% --es sismo %~2 & goto fin)
goto ayuda

:reinstalar
rem Borra la app (y su historial) para poder instalar una firmada en otra computadora.
"%ADB%" uninstall %APP%
:instalar
echo Conecta el telefono con la depuracion USB activada y acepta el aviso que aparece en su pantalla.
"%ADB%" wait-for-device
rem Si ehria-debug.apk ya esta en esta carpeta, se usa esa; si no, se descarga.
if exist "%AQUI%ehria-debug.apk" goto apkLista
echo Descargando la app de prueba...
call :descargar "%URL_APK%" "%AQUI%ehria-debug.apk" || (echo No se pudo descargar la app. Copia ehria-debug.apk en esta carpeta y vuelve a intentar. & goto fin)
:apkLista
"%ADB%" install -r "%AQUI%ehria-debug.apk" || (echo. & echo Si dice INSTALL_FAILED_UPDATE_INCOMPATIBLE, la app del telefono se instalo desde otra computadora. Usa: ehria.bat reinstalar ^(borra el historial de la app^) & goto fin)
:abrir
"%ADB%" shell am start -n %APP%/.MainActivity
goto fin

:ayuda
echo.
echo  ehria.bat instalar            instala la app de prueba (ehria-debug.apk en esta carpeta, o la descarga) y la abre
echo  ehria.bat telefono            muestra si el telefono esta conectado
echo  ehria.bat conectar            cara del robot, como si estuviera en la estacion
echo  ehria.bat cerca               alguien frente a la estacion: despierta, saluda y escucha
echo  ehria.bat lejos               nadie cerca (se duerme a los 45 s)
echo  ehria.bat calor               34 grados y 75%% de humedad, con alguien cerca
echo  ehria.bat ambiente "25,60,-1" temperatura, humedad, distancia en cm (-1 = nadie; "no" = detener)
echo  ehria.bat decir "ehria abre el botiquin"    como si se lo dijeran al microfono
echo  ehria.bat expresion todas     recorre todas las expresiones (o FELIZ, TRISTE, CORAZON...)
echo  ehria.bat sismo fuerte        sismo simulado: leve, moderado o fuerte
echo  ehria.bat desconectar         modo primeros auxilios
echo.
echo  Escribe las frases sin tildes ni signos: "ehria como hago rcp", "ehria que temperatura hace".
goto fin

rem Descarga %1 en %2: con curl (Windows 10 y 11) o, si no esta, con PowerShell.
:descargar
"%SystemRoot%\System32\curl.exe" -L -f -s -S -o "%~2" "%~1" 2>nul && exit /b 0
powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol='Tls12'; Invoke-WebRequest -UseBasicParsing -Uri '%~1' -OutFile '%~2'" && exit /b 0
exit /b 1

:fin
endlocal
