@echo off
setlocal
rem 一键发布/更新「局内卡牌图鉴」到 Steam 创意工坊（app 646570）。
rem 前置：Steam 已启动并登录；游戏与 ModTheSpire 关闭。
rem config.json 里有 steamPublishedID 就是更新；没有这个键就是新建条目（上传器自己写回 ID）。
chcp 65001 >nul
cd /d "D:\Steam\steamapps\common\SlayTheSpire"

set "WS=%~dp0"
if "%WS:~-1%"=="\" set "WS=%WS:~0,-1%"

echo Uploading "In-Run Card Compendium" to Steam Workshop...
echo Workspace: %WS%
echo.

".\jre\bin\java.exe" -Dfile.encoding=UTF-8 -jar "%WS%\mod-uploader.jar" upload -w "%WS%"
echo.
echo Exit code: %ERRORLEVEL%
pause
