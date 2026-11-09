; laptop/build/installer.nsh
; NSIS safety net: kill any surviving engine process before install/update
!macro customInit
  nsExec::Exec 'taskkill /F /IM dhyan-engine.exe /T'
!macroend
