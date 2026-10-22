# Puente entre el Arduino conectado a esta computadora y el telefono conectado por USB.
# El telefono llega aqui con "adb reverse tcp:8777 tcp:8777" (lo hace ehria.bat puente).
# Uso: powershell -ExecutionPolicy Bypass -File puente.ps1 [COM3]
# Cerrar esta ventana (o Ctrl+C) es como sacar el telefono de la estacion.
param([string]$Puerto = "", [int]$PuertoTcp = 8777)

$puertos = [System.IO.Ports.SerialPort]::GetPortNames() | Sort-Object
if (-not $Puerto) {
    if (-not $puertos) {
        Write-Host "No encuentro el Arduino. Conectalo a la computadora y cierra el Monitor Serie del Arduino IDE."
        exit 1
    }
    # El ultimo puerto suele ser el del Arduino recien conectado.
    $Puerto = $puertos[-1]
    if (@($puertos).Count -gt 1) {
        Write-Host "Puertos encontrados: $($puertos -join ', '). Uso $Puerto (para otro: ehria.bat puente COM4)."
    }
}

$servidor = New-Object System.Net.Sockets.TcpListener ([System.Net.IPAddress]::Loopback, $PuertoTcp)
$servidor.Start()
Write-Host "Puente listo: Arduino en $Puerto <-> telefono. Ctrl+C para terminar."

try {
    while ($true) {
        $cliente = $servidor.AcceptTcpClient()
        $cliente.NoDelay = $true
        $red = $cliente.GetStream()
        $serie = New-Object System.IO.Ports.SerialPort $Puerto, 115200, 'None', 8, 'One'
        $serie.DtrEnable = $true
        try {
            $serie.Open()
        } catch {
            Write-Host "No se pudo abrir $Puerto. Esta abierto en el Arduino IDE u otro programa?"
            $cliente.Close()
            Start-Sleep -Seconds 2
            continue
        }
        Write-Host "Telefono conectado. Pasando datos..."
        $deArduino = New-Object byte[] 1024
        $deTelefono = New-Object byte[] 256
        try {
            while ($true) {
                $hubo = $false
                $n = $serie.BytesToRead
                if ($n -gt 0) {
                    $n = $serie.Read($deArduino, 0, [Math]::Min($n, $deArduino.Length))
                    $red.Write($deArduino, 0, $n)
                    $hubo = $true
                }
                if ($red.DataAvailable) {
                    $n = $red.Read($deTelefono, 0, $deTelefono.Length)
                    if ($n -le 0) { break }
                    $serie.Write($deTelefono, 0, $n)
                    $hubo = $true
                } elseif ($cliente.Client.Poll(0, [System.Net.Sockets.SelectMode]::SelectRead)) {
                    # Hay "algo para leer" pero no datos: el telefono cerro la conexion.
                    break
                }
                if (-not $hubo) { Start-Sleep -Milliseconds 5 }
            }
        } catch {
            Write-Host "Se corto la conexion: $($_.Exception.Message)"
        } finally {
            $serie.Close()
            $cliente.Close()
        }
        Write-Host "Telefono desconectado. Esperando que vuelva..."
    }
} finally {
    $servidor.Stop()
}
