; GupTa Laptop Server — Inno Setup installer script
; Download Inno Setup from https://jrsoftware.org/isdl.php
; To compile: right-click installer.iss -> Compile

[Setup]
AppName=GupTa Server
AppVersion=1.0
DefaultDirName={autopf}\GupTaServer
DefaultGroupName=GupTa Server
UninstallDisplayIcon={app}\VoiceCommandServer.exe
Compression=lzma2
SolidCompression=yes
OutputDir=.
OutputBaseFilename=GupTaServer_Installer
PrivilegesRequired=admin
DisableProgramGroupPage=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop shortcut"; GroupDescription: "Additional icons:"

[Files]
; Main executable and scripts
Source: "VoiceCommandServer.exe";  DestDir: "{app}"
Source: "bridge_server.py";        DestDir: "{app}"
Source: "wake_bridge.py";          DestDir: "{app}"
Source: "whisper_http_server.py";  DestDir: "{app}"
Source: "security.py";             DestDir: "{app}"
Source: "requirements.txt";        DestDir: "{app}"
Source: "setup.bat";               DestDir: "{app}"
Source: "start.bat";               DestDir: "{app}"

; Bundled Python — no system Python install needed
Source: "python\*"; DestDir: "{app}\python"; Flags: recursesubdirs

[Icons]
Name: "{group}\GupTa Server";           Filename: "{app}\VoiceCommandServer.exe"
Name: "{commondesktop}\GupTa Server";   Filename: "{app}\VoiceCommandServer.exe"; Tasks: desktopicon
Name: "{group}\Uninstall GupTa Server"; Filename: "{uninstallexe}"

[Run]
; Run setup automatically after install (installs pip packages + firewall rules)
Filename: "{app}\setup.bat"; Description: "Install server dependencies (required)"; Flags: postinstall nowait shellexec

[UninstallRun]
Filename: "{cmd}"; Parameters: "/c rmdir /s /q ""{app}\python\Lib\site-packages"""; Flags: runhidden
