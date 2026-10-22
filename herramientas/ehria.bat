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
curl -L -f -o "%AQUI%platform-tools.zip" "%URL_ADB%" || (echo No se pudo descargar adb. Revisa la conexion a internet. & goto fin)
tar -xf "%AQUI%platform-tools.zip" -C "%AQUI%" || (echo No se pudo descomprimir adb. & goto fin)
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
curl -L -f -o "%AQUI%ehria-debug.apk" "%URL_APK%" || (echo No se pudo descargar la app. Copia ehria-debug.apk en esta carpeta y vuelve a intentar. & goto fin)
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
:fin
endlocal
