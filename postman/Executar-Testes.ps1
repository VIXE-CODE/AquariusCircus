param(
    [string]$Projeto='C:\Users\tibia\IdeaProjects\gestao-alunos',
    [int]$Porta=8081,
    [string]$DiretorioRelatorios=$PSScriptRoot,
    [string]$ModuloNewman='',
    [switch]$PularBuild
)
$ErrorActionPreference='Stop'
$Projeto=[System.IO.Path]::GetFullPath($Projeto)
if($Porta -eq 8080 -or $Porta -lt 1024 -or $Porta -gt 65535){throw 'Use uma porta de testes diferente de 8080.'}
if(!(Test-Path -LiteralPath (Join-Path $Projeto 'pom.xml'))){throw 'Projeto Maven não encontrado.'}
$runtime=Join-Path $Projeto 'target/postman'
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$javaCandidates=@()
if($env:JAVA_HOME){$javaCandidates+=(Join-Path $env:JAVA_HOME 'bin/java.exe')}
$javaCandidates+=@(
    (Join-Path $env:USERPROFILE '.jdks/corretto-17.0.21/bin/java.exe'),
    'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe'
)
$javaExe=$javaCandidates|Where-Object {Test-Path -LiteralPath $_}|Select-Object -First 1
if(!$javaExe){$javaExe=(Get-Command java -ErrorAction Stop).Source}
$env:JAVA_HOME=Split-Path (Split-Path $javaExe)
$repo=Join-Path $env:USERPROFILE '.m2/repository'
if(!$PularBuild){
    $maven=Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.m2/wrapper/dists') -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue|Select-Object -First 1 -ExpandProperty FullName
    if(!$maven){$maven=Join-Path $Projeto 'mvnw.cmd'}
    Push-Location -LiteralPath $Projeto
    try {
        $build=& $maven -B -ntp -DskipTests package 2>&1
        $code=$LASTEXITCODE
        $build|Set-Content -LiteralPath (Join-Path $runtime 'build.log') -Encoding UTF8
        if($code -ne 0){$build|Select-Object -Last 35;throw 'A compilação falhou.'}
    } finally {Pop-Location}
}
$jar=Join-Path $Projeto 'target/gestao-alunos-0.0.1-SNAPSHOT.jar'
if(!(Test-Path -LiteralPath $jar)){throw 'JAR não encontrado. Execute sem -PularBuild.'}
if(!$ModuloNewman){$ModuloNewman=Join-Path $runtime 'tools/node_modules/newman'}
if(!(Test-Path -LiteralPath (Join-Path $ModuloNewman 'package.json'))){
    $tools=Join-Path $runtime 'tools'
    & npm.cmd install --prefix $tools --cache (Join-Path $runtime 'npm-cache') --no-audit --no-fund newman@6.2.1
    if($LASTEXITCODE -ne 0){throw 'Não foi possível instalar Newman localmente.'}
}
$jars=@(
    (Join-Path $repo 'org/postgresql/postgresql/42.6.2/postgresql-42.6.2.jar'),
    (Join-Path $repo 'org/springframework/security/spring-security-crypto/6.2.3/spring-security-crypto-6.2.3.jar'),
    (Join-Path $repo 'org/springframework/spring-jcl/6.1.5/spring-jcl-6.1.5.jar')
)
foreach($dependency in $jars){if(!(Test-Path -LiteralPath $dependency)){throw "Dependência não encontrada: $dependency"}}
$nodeExe=(Get-Command node -ErrorAction Stop).Source
& $javaExe --class-path ($jars -join ';') (Join-Path $PSScriptRoot 'RunPostman.java') `
    $Projeto $jar $nodeExe $ModuloNewman $PSScriptRoot $runtime $Porta $DiretorioRelatorios
if($LASTEXITCODE -ne 0){throw 'Teste de integração falhou. Consulte os relatórios e target/postman/api-postman.log.'}
Write-Output "Relatórios salvos em: $DiretorioRelatorios"
