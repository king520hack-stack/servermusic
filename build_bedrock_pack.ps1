$ErrorActionPreference = "Stop"
$targetDir = "aurun_music_bedrock"
if (Test-Path $targetDir) { Remove-Item -Recurse -Force $targetDir }
New-Item -ItemType Directory -Force -Path "$targetDir\sounds\music" | Out-Null
New-Item -ItemType Directory -Force -Path "$targetDir\texts" | Out-Null

# Copia os arquivos .ogg
Copy-Item "resourcepack\assets\aurun\sounds\music\*.ogg" -Destination "$targetDir\sounds\music"

# Converter traduções JSON para LANG (Bedrock)
$langFiles = Get-ChildItem "resourcepack\assets\aurun\lang\*.json"
foreach ($file in $langFiles) {
    $langName = $file.BaseName
    $content = Get-Content $file.FullName | ConvertFrom-Json
    $langLines = @()
    foreach ($property in $content.psobject.properties) {
        $key = $property.Name
        $value = $property.Value
        $langLines += "$key=$value"
    }
    
    # Bedrock espera nomes de arquivos específicos (ex: pt_BR.lang)
    $bedrockLangName = $langName
    if ($langName -eq "pt_br") { $bedrockLangName = "pt_BR" }
    elseif ($langName -eq "en_us") { $bedrockLangName = "en_US" }
    elseif ($langName -eq "es_es") { $bedrockLangName = "es_ES" }
    
    $langLines | Set-Content -Path "$targetDir\texts\$bedrockLangName.lang" -Encoding UTF8
}

# Criar languages.json
$languagesJson = '["en_US", "pt_BR", "es_ES"]'
Set-Content -Path "$targetDir\texts\languages.json" -Value $languagesJson -Encoding UTF8

# Gera manifest.json
$uuid1 = [guid]::NewGuid().ToString()
$uuid2 = [guid]::NewGuid().ToString()
$manifest = @"
{
  "format_version": 2,
  "header": {
    "description": "AurunMusic Bedrock Pack",
    "name": "AurunMusic Bedrock",
    "uuid": "$uuid1",
    "version": [1, 0, 0],
    "min_engine_version": [1, 16, 0]
  },
  "modules": [
    {
      "description": "AurunMusic Bedrock Pack",
      "type": "resources",
      "uuid": "$uuid2",
      "version": [1, 0, 0]
    }
  ]
}
"@
Set-Content -Path "$targetDir\manifest.json" -Value $manifest -Encoding UTF8

# Gera sound_definitions.json
# Nota: O Geyser mapeia automaticamente sons Java para Bedrock mantendo o nome exato.
$soundDef = @"
{
  "format_version": "1.14.0",
  "sound_definitions": {
"@

$files = Get-ChildItem "$targetDir\sounds\music\*.ogg"
$count = 0
foreach ($file in $files) {
    $name = $file.BaseName
    $soundDef += @"
    "aurun:music.$name": {
      "category": "record",
      "sounds": [
        "sounds/music/$name"
      ]
    }
"@
    $count++
    if ($count -lt $files.Count) {
        $soundDef += ","
    }
}

$soundDef += @"
  }
}
"@
Set-Content -Path "$targetDir\sounds\sound_definitions.json" -Value $soundDef -Encoding UTF8

# Criar pack_icon.png falso (alguns clientes exigem ícone)
$imgBytes = [Convert]::FromBase64String("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")
[System.IO.File]::WriteAllBytes("$targetDir\pack_icon.png", $imgBytes)

# Zipa para .mcpack
if (Test-Path "aurun_music_bedrock.mcpack") { Remove-Item "aurun_music_bedrock.mcpack" -Force }
if (Test-Path "aurun_music_bedrock.zip") { Remove-Item "aurun_music_bedrock.zip" -Force }
Compress-Archive -Path "$targetDir\*" -DestinationPath "aurun_music_bedrock.zip" -Force
Start-Sleep -Seconds 1
Rename-Item "aurun_music_bedrock.zip" "aurun_music_bedrock.mcpack" -Force

# Limpa diretório temporário
Remove-Item -Recurse -Force $targetDir
Write-Host "Bedrock Pack gerado com traducoes e icone: aurun_music_bedrock.mcpack"
