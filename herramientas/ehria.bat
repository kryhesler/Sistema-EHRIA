@echo off
rem Probar la estacion EHRIA sin Arduino ni cable OTG (emulador o telefono con depuracion USB).
rem Uso: herramientas\ehria.bat <orden> [valor]     (sin argumentos muestra la ayuda)
setlocal
set "APP=com.example.detectormovimiento"
set "ACCION=com.example.detectormovimiento.SIMULAR_ARDUINO"
set "ADB=adb"
where adb >nul 2>nul || set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "ENVIAR="%ADB%" shell am broadcast -a %ACCION% -p %APP%"

if /i "%~1"=="instalar"    goto instalar
if /i "%~1"=="abrir"       goto abrir
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

:instalar
rem Compila la app de depuracion e instala en el emulador o telefono conectado.
pushd "%~dp0.."
call gradlew.bat assembleDebug || (popd & goto fin)
"%ADB%" install -r app\build\outputs\apk\debug\app-debug.apk
popd
:abrir
"%ADB%" shell am start -n %APP%/.MainActivity
goto fin

:ayuda
echo.
echo  herramientas\ehria.bat instalar            compila, instala y abre la app
echo  herramientas\ehria.bat conectar            simula que el telefono esta en la estacion (cara del robot)
echo  herramientas\ehria.bat desconectar         simula sacar el telefono (modo primeros auxilios)
echo  herramientas\ehria.bat cerca               alguien frente a la estacion (despierta y saluda)
echo  herramientas\ehria.bat lejos               nadie cerca (se duerme a los 45 s)
echo  herramientas\ehria.bat calor               34 grados y 75%% de humedad, con alguien cerca
echo  herramientas\ehria.bat ambiente "25,60,-1" temperatura, humedad, distancia en cm (-1 = nadie; "no" = detener)
echo  herramientas\ehria.bat decir "ehria abre el botiquin"    como si se lo dijeran al microfono
echo  herramientas\ehria.bat expresion todas     recorre todas las expresiones (o FELIZ, TRISTE, CORAZON...)
echo  herramientas\ehria.bat sismo fuerte        sismo simulado: leve, moderado o fuerte
echo.
echo  Escribe las frases sin tildes ni signos: "ehria como hago rcp", "ehria que temperatura hace".
:fin
endlocal
