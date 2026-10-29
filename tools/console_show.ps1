<#
시연 녹화용 자동 운전. Pleos Virtual Console의 HTTP API(adb forward tcp:8088)로 차를 몰고,
adb로 앱의 "모험 시작"을 누른 뒤 신호 표시·자막을 켠다.

  출발 → 가속 → 우회전 → 정차(용) → 비 → 정차(여우) → 비 그침(무지개) → 정차(올빼미) → 주차 → 엔딩

터널·회전 예고·진행률은 내비 신호라 콘솔로 못 만든다 (데모 코스에서 보여 준다).

  powershell -File tools\console_show.ps1            # 정차마다 25초
  powershell -File tools\console_show.ps1 -StopSeconds 30
#>
param(
    [int]$StopSeconds = 25,
    [switch]$SkipAppLaunch
)

$ErrorActionPreference = 'Stop'
$console = 'http://localhost:8088'
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
# 앱 영역(1680x1200)이 에뮬레이터 화면 (855,90)에 있을 때의 좌표
$adventureButton = '1455 810'
$sceneTitle = '935 145'

$speed = 0.0
$steering = 0

function Log($msg) { Write-Host ("{0:HH:mm:ss} {1}" -f (Get-Date), $msg) }

# 콘솔 API는 브라우저 세션 하트비트가 있어야 받는다(emulator_inactive). 콘솔 창이 가려지면
# 그 창의 하트비트가 느려지므로 스크립트가 직접 보낸다.
$lastBeat = [DateTime]::MinValue
function Heartbeat {
    if (((Get-Date) - $script:lastBeat).TotalMilliseconds -lt 700) { return }
    Invoke-RestMethod -Method Post "$console/browser/session?client=running-tale-show" | Out-Null
    $script:lastBeat = Get-Date
}

function Put($path) {
    Heartbeat
    Invoke-RestMethod -Method Put "$console$path" | Out-Null
}

# 차속은 직접 넣는다. 가속 페달 값을 같이 주면 콘솔 화면이 그 값으로 스스로 가속해 차속을 덮어쓰므로 0으로 둔다.
function Send-Controls {
    $brake = if ($speed -le 0) { 30 } else { 0 }
    Put ("/vehicle/controls?speed={0}&accelerator=0&brake={1}&steering={2}" -f [int]$speed, $brake, $steering)
}

# 차속을 seconds 동안 부드럽게 바꾸고, 그동안에도 0.25초마다 신호를 보낸다.
function Ramp([double]$to, [double]$seconds) {
    $from = $script:speed
    $steps = [Math]::Max(1, [int]($seconds * 4))
    for ($i = 1; $i -le $steps; $i++) {
        $script:speed = $from + ($to - $from) * $i / $steps
        Send-Controls
        Start-Sleep -Milliseconds 250
    }
}

# 지금 속도·조향을 유지하며 기다린다 (콘솔이 값을 잊지 않게 계속 보낸다).
function Hold([double]$seconds) {
    $end = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $end) {
        Send-Controls
        Start-Sleep -Milliseconds 300
    }
}

function Wiper($mode) { Put "/vehicle/body?front_wiper=$mode" }

try {
    Log '준비: P, 정지, 와이퍼 끔'
    $speed = 0; $steering = 0
    Put '/vehicle/gear?gear=Park'
    Send-Controls
    Wiper 'Off'

    if (-not $SkipAppLaunch) {
        Log '앱: 모험 시작 + 신호 표시'
        & $adb shell am force-stop com.runningtale.app
        & $adb shell am start -n com.runningtale.app/.MainActivity | Out-Null
        Start-Sleep 4
        & $adb shell input tap $adventureButton.Split(' ')
        Start-Sleep 3
        & $adb shell input swipe ($sceneTitle.Split(' ') + $sceneTitle.Split(' ') + '1200')
    }
    Hold 5   # 시작 낭독 (그동안에도 하트비트·정지 신호를 보낸다)

    Log '출발'
    Put '/vehicle/gear?gear=Drive'
    Ramp 22 4
    Hold 5

    Log '가속'
    Ramp 48 4
    Hold 4

    Log '우회전'
    $steering = 250; Hold 2.5
    $steering = 0; Hold 6

    Log "정차 1: 용 ($StopSeconds 초)"
    Ramp 0 3
    Hold $StopSeconds

    Log '재출발 → 비'
    Ramp 35 3
    Wiper 'Low'
    Hold 8

    Log "정차 2: 여우 ($StopSeconds 초)"
    Ramp 0 3
    Hold $StopSeconds

    Log '재출발 → 비 그침, 무지개'
    Ramp 35 3
    Wiper 'Off'
    Hold 7

    Log "정차 3: 올빼미 ($StopSeconds 초)"
    Ramp 0 3
    Hold $StopSeconds

    Log '성으로'
    Ramp 35 3
    Hold 5
    Ramp 0 3

    Log '주차 (P 유지 후 엔딩)'
    Put '/vehicle/gear?gear=Park'
    Hold 35
    Log '끝'
}
finally {
    $speed = 0; $steering = 0
    try { Send-Controls } catch {}
}
