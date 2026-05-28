Set ws = CreateObject("WScript.Shell")
desktop = ws.SpecialFolders("Desktop")
folder = CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName)

Set sc = ws.CreateShortcut(desktop & "\GupTa Server.lnk")
sc.TargetPath = folder & "\VoiceCommandServer.exe"
sc.WorkingDirectory = folder
sc.Description = "GupTa Server"
sc.Save

MsgBox "Shortcut created on your desktop!" & vbCrLf & vbCrLf & _
       "Double-click 'GupTa Server' on your desktop to launch.", vbInformation, "Done"
