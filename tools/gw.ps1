# 在工作区内运行 Gradle 的统一入口喵~
#
# 本机环境有三个坑，这个脚本一起解决：
#
# 1) DSH 文件沙箱只允许写「会话工作区」与少量平台临时目录，而 Gradle 默认要写
#    ~/.gradle（wrapper 锁文件、依赖缓存、daemon 日志）→ 写被拒。
#    解决：GRADLE_USER_HOME 指向可写的位置；并用 GRADLE_RO_DEP_CACHE **只读**复用
#          ~/.gradle/caches 里已下载的依赖（不重新下载）。
#
# 2) 【关键】GRADLE_USER_HOME 与构建目录都必须是**纯 ASCII 路径**。
#    实测：Gradle 用 UTF-8 写 worker 的类路径 argfile（@file），而 JDK 启动器在 Windows 上
#    按系统 ANSI 代码页（本机 936/GBK）解析它 → 中文路径「工作目录」被解码成乱码
#    「宸ヤ綔鐩綍」→ worker 找不到主类，报
#    ClassNotFoundException: GradleWorkerMain，表现为 :neoFormRecompile 失败。
#    （已用字节级对照确认：同一份 argfile 按 UTF-8 解出正确路径，按 936 解出乱码。）
#
# 3) 工作区里 .gradle / src / run / docs / config-examples 这些**改动前就存在**的子目录
#    缺少沙箱的写入授权项（同级的 build / tools 有，新建目录也有），
#    于是 Gradle 写 <项目>/.gradle 与 build/reports/... 时被 Windows 拒绝；
#    另有 build/reports/problems/problems-report.html 带一条不可继承的历史权限项，
#    连删除都不允许。
#    解决：默认在**ASCII 临时目录**里做镜像构建（新建目录自动带授权项、无历史 ACL 遗留），
#          构建前把源码同步过去，保证「构建的就是真源码」；产物再拷回真实 build/libs。
#          加 -InPlace 可强制原地构建（需先修好上面第 3 条的权限）。
#
# 用法：
#   .\tools\gw.ps1 build --console=plain            # 镜像构建（默认，推荐）
#   .\tools\gw.ps1 runGameTestServer                # 跑游戏测试
#   .\tools\gw.ps1 clean build -NoSync              # 跳过源码同步（调试用）
#   .\tools\gw.ps1 build -InPlace                   # 原地构建
param(
    [switch]$InPlace,
    [switch]$NoSync,
    [switch]$KeepArtifacts,
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArgs
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

# ---- 可写的 ASCII 根（见文件头第 1、2 条）----
$asciiRoot = if ($env:TEMP -match '^[\x20-\x7E]+$') { $env:TEMP } else { 'C:\Windows\Temp' }
$home_ = Join-Path $asciiRoot 'sm-gradle-home'
New-Item -ItemType Directory -Path $home_ -Force | Out-Null
if ($home_ -notmatch '^[\x20-\x7E]+$') {
    throw "GRADLE_USER_HOME 路径含非 ASCII 字符，会导致 worker 启动失败：$home_"
}

# 写入本脚本专用的 gradle.properties：不直接复制全局那份。
#
# 为什么不直接复制：全局 ~/.gradle/gradle.properties 里写死了 Clash 代理
# （systemProp.http(s).proxyHost=127.0.0.1:7897）。代理没开时，Gradle 所有下载都会
# 立刻 "Connection refused" 而失败——即使本机**直连**完全正常
# （实测：Java 直连 piston-meta 返回 200/449557 字节，而走代理则被拒）。
# 所以这里按「代理端口是否真的在监听」决定要不要带代理配置，避免代理开关状态
# 影响构建能否进行喵~
$proxyPort = 7897
$proxyAlive = $false
try {
    $client = New-Object System.Net.Sockets.TcpClient
    $proxyAlive = $client.ConnectAsync('127.0.0.1', $proxyPort).Wait(600)
    $client.Close()
} catch { $proxyAlive = $false }

$propsPath = Join-Path $home_ 'gradle.properties'
$lines = @(
    '# 由 tools/gw.ps1 生成：本机构建专用配置喵~',
    'org.gradle.daemon=true',
    'org.gradle.caching=true',
    'org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8',
    'org.gradle.logging.level=lifecycle',
    'systemProp.org.gradle.internal.http.connectionTimeout=180000',
    'systemProp.org.gradle.internal.http.socketTimeout=180000'
)
if ($proxyAlive) {
    Write-Host "[gw] 代理 127.0.0.1:$proxyPort 可用，构建将走代理"
    $lines += "systemProp.http.proxyHost=127.0.0.1"
    $lines += "systemProp.http.proxyPort=$proxyPort"
    $lines += "systemProp.https.proxyHost=127.0.0.1"
    $lines += "systemProp.https.proxyPort=$proxyPort"
} else {
    Write-Host "[gw] 代理 127.0.0.1:$proxyPort 未监听，构建将直连（本机直连实测可用）"
}
Set-Content -LiteralPath $propsPath -Value ($lines -join "`n") -Encoding utf8

$env:GRADLE_USER_HOME = $home_
$env:GRADLE_RO_DEP_CACHE = Join-Path $env:USERPROFILE '.gradle\caches'

# ---- 定位本机已解压的 Gradle 发行版（优先用 wrapper 声明的那一版）----
$distRoot = Join-Path $env:USERPROFILE '.gradle\wrapper\dists'
$wanted = $null
$wrapperProps = Join-Path $root 'gradle\wrapper\gradle-wrapper.properties'
if (Test-Path $wrapperProps) {
    $m = Select-String -Path $wrapperProps -Pattern 'gradle-([0-9.]+)-(bin|all)\.zip' -ErrorAction SilentlyContinue
    if ($m) { $wanted = $m.Matches[0].Groups[1].Value }
}
$all = Get-ChildItem -Path (Join-Path $distRoot '*\*\gradle-*\bin\gradle.bat') -ErrorAction SilentlyContinue
$gradleBat = $null
if ($wanted) {
    $gradleBat = $all |
        Where-Object { $_.FullName -match "\\gradle-$([regex]::Escape($wanted))\\bin\\gradle\.bat$" } |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $gradleBat) {
    $gradleBat = $all | Sort-Object -Property FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $gradleBat) { throw "找不到本机已解压的 Gradle 发行版：$distRoot" }

# ---- 选定构建目录 ----
if ($InPlace) {
    $projectDir = $root
    Write-Host '[gw] 模式：原地构建（使用真实项目目录）'
} else {
    $projectDir = Join-Path $asciiRoot 'sm-build\Sweeper-Maid'
    New-Item -ItemType Directory -Path $projectDir -Force | Out-Null

    foreach ($f in @('build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat', '.gitattributes')) {
        $src = Join-Path $root $f
        if (Test-Path -LiteralPath $src) { Copy-Item -LiteralPath $src -Destination $projectDir -Force }
    }

    if (-not $NoSync) {
        # /MIR 让镜像与源码严格一致，避免「验了旧代码」这种假绿喵~
        robocopy (Join-Path $root 'src') (Join-Path $projectDir 'src') /MIR /NFL /NDL /NJH /NJS /NP | Out-Null
        if ($LASTEXITCODE -ge 8) { throw "同步 src 到构建镜像失败，robocopy 退出码 $LASTEXITCODE" }
        robocopy (Join-Path $root 'gradle') (Join-Path $projectDir 'gradle') /MIR /NFL /NDL /NJH /NJS /NP | Out-Null
    }

    # 镜像目录名与真实项目不同，需固定 rootProject.name，否则产物名会变成 Sweeper-Maid 之外的名字喵~
    # 必须**追加在文件末尾**：settings.gradle 里 pluginManagement{} / plugins{} 之前不允许出现其它语句喵~
    $baseSettings = Get-Content -LiteralPath (Join-Path $root 'settings.gradle') -Raw
    Set-Content -LiteralPath (Join-Path $projectDir 'settings.gradle') `
        -Value ($baseSettings.TrimEnd() + "`n`nrootProject.name = 'Sweeper-Maid'`n") -NoNewline
    Write-Host "[gw] 模式：镜像构建 → $projectDir"
}

Write-Host "[gw] GRADLE_USER_HOME = $home_"
Write-Host "[gw] gradle = $gradleBat"

# 镜像构建时让 Gradle 用默认的 <项目>/.gradle 即可：镜像在 ASCII 临时目录里，
# 既可写又没有历史 ACL 遗留 —— 因此**不需要** --project-cache-dir，
# 也就不必为了它去关掉文件系统监视（Gradle 不允许这两者同时启用）喵~
& $gradleBat @GradleArgs --project-dir $projectDir
$code = $LASTEXITCODE

# ---- 清理过期产物 ----
# Gradle 不会删除旧版本号的 jar：改了 mod_version 后，build/libs 里会同时留着
# sweeper_maid-2.1.0.jar 与 sweeper_maid-2.1.1.jar；而镜像目录是**跨次复用**的，
# 于是旧 jar 会被一遍遍拷回真实仓库，越积越多（发包时极易挑错文件）。
# 这里按 gradle.properties 的当前版本精确清理**本模组自己的**产物，
# 不碰目录里其它文件喵~
$props = Get-Content -LiteralPath (Join-Path $root 'gradle.properties') -Raw
$modId = [regex]::Match($props, '(?m)^\s*mod_id\s*=\s*(\S+)').Groups[1].Value
$modVer = [regex]::Match($props, '(?m)^\s*mod_version\s*=\s*(\S+)').Groups[1].Value
if ($modId -and $modVer) {
    $keepPrefix = "$modId-$modVer"
    $dirsToClean = @()
    if (-not $InPlace) { $dirsToClean += (Join-Path $projectDir 'build\libs') }
    $dirsToClean += (Join-Path $root 'build\libs')
    foreach ($dir in $dirsToClean) {
        if (-not (Test-Path -LiteralPath $dir)) { continue }
        Get-ChildItem -LiteralPath $dir -File -Filter "$modId-*.jar" -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notlike "$keepPrefix*" } |
            ForEach-Object {
                Remove-Item -LiteralPath $_.FullName -Force -ErrorAction SilentlyContinue
                Write-Host "[gw] 清理过期产物：$($_.Name)"
            }
    }
}

# ---- 把产物拷回真实仓库，便于交付与后续实机测试 ----
if (-not $InPlace -and -not $KeepArtifacts) {
    $fromLibs = Join-Path $projectDir 'build\libs'
    $toLibs = Join-Path $root 'build\libs'
    if (Test-Path $fromLibs) {
        New-Item -ItemType Directory -Path $toLibs -Force | Out-Null
        Get-ChildItem $fromLibs -File -ErrorAction SilentlyContinue | ForEach-Object {
            try { Copy-Item $_.FullName -Destination $toLibs -Force -ErrorAction Stop; Write-Host "[gw] 产物已拷回：build/libs/$($_.Name)" }
            catch { Write-Host "[gw] 产物拷回失败（不影响构建结果）：$($_.Name) — $($_.Exception.Message)" }
        }
    }
}

exit $code
