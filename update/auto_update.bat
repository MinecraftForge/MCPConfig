@echo off

REM Requires python3+, and python wget installed https://pypi.org/project/wget/

REM Update these as nessasary
set MAP_TOY=1.0.7
set DEPIG=1.0.4

REM Basic paths, shouldn't need changing
set MC_ROOT=%APPDATA%\.minecraft
set MCP_CONFIG=..
set MAP_DATA=output
set BIN_DIR=bin

REM Read arguments
set OLD_VERSION=%1
set NEW_VERSION=%2
set OLD_PATH=%3
set NEW_PATH=%4

if [%OLD_PATH%] == [] (
  set OLD_PATH=release
)

if [%NEW_PATH%] == [] (
  set NEW_PATH=%OLD_PATH%
)

set OLD_PATH=%MCP_CONFIG%\versions\%OLD_PATH%\%OLD_VERSION%
set NEW_PATH=%MCP_CONFIG%\versions\%NEW_PATH%\%NEW_VERSION%

set MIGRATE_DIR=%MAP_DATA%\%OLD_VERSION%_to_%NEW_VERSION%

echo Starting %OLD_VERSION% -^> %NEW_VERSION%
echo MC Root : %MC_ROOT%
echo MCP Cfg : %MCP_CONFIG%
echo Old Data: %OLD_PATH%
echo New Data: %NEW_PATH%
echo Data    : %MAP_DATA%
echo Bin     : %BIN_DIR%
echo Migrate : %MIGRATE_DIR%
echo Java    : %JAVA_HOME%
echo.

if not exist %BIN_DIR% (
    mkdir %BIN_DIR%
)

set MAP_TOY_FILE=%BIN_DIR%\MappingToy-%MAP_TOY%-all.jar
if not exist %MAP_TOY_FILE% (
    echo Downloading Mapping Toy: %MAP_TOY%
    python -m wget -o "%MAP_TOY_FILE%" "https://files.minecraftforge.net/maven/net/minecraftforge/lex/MappingToy/%MAP_TOY%/MappingToy-%MAP_TOY%-all.jar"
    echo.
)
if not exist %MAP_TOY_FILE% GOTO :EOF

"%JAVA_HOME%/bin/java.exe" -jar "%MAP_TOY_FILE%" --libs --output %MAP_DATA%\ --mc %MC_ROOT%\ --version %OLD_VERSION% --version %NEW_VERSION%

set OLD_MAP=%MAP_DATA%\%OLD_VERSION%\client.txt
if not exist "%OLD_MAP%" (
    echo Missing Old Map: %OLD_MAP%
    GOTO :EOF
)
echo Old Map : %OLD_MAP%
set NEW_MAP=%MAP_DATA%\%NEW_VERSION%\client.txt
if not exist "%NEW_MAP%" (
    echo Missing New Map: %NEW_MAP%
    GOTO :EOF
)
echo New Map : %NEW_MAP%
echo.

REM rm -rf old
REM echo Extracting: mcps\%OLD_VERSION%.zip -^> old
REM unzip -q mcps\%OLD_VERSION%.zip -d old
REM mkdir old\jars\versions\%OLD_VERSION%\
REM copy output\%OLD_VERSION%\joined_a.jar old\jars\versions\%OLD_VERSION%\%OLD_VERSION%_joined.jar

REM pushd old
REM   py runtime\decompile.py --joined -p -a -n
REM popd

REM rm -rf new
REM echo Extracting: mcps\%OLD_VERSION%.zip -^> new
REM unzip -q mcps\%OLD_VERSION%.zip -d new
REM py %SCRIPTS%\UpdateClasspath.py %NEW_VERSION% new

set DEPIG_FILE=%BIN_DIR%\depigifier-%DEPIG%-fatjar.jar
if not exist %DEPIG_FILE% (
    echo Downloading Depigifier: %DEPIG%
    python -m wget -o "%DEPIG_FILE%" "https://files.minecraftforge.net/maven/net/minecraftforge/depigifier/%DEPIG%/depigifier-%DEPIG%-fatjar.jar"
    echo.
)
if not exist %DEPIG_FILE% GOTO :EOF

echo Running First Depigifer
"%JAVA_HOME%/bin/java.exe" -jar %DEPIG_FILE% --oldPG %OLD_MAP% --newPG %NEW_MAP% --out %MIGRATE_DIR%\pig\

echo Fix any suggestions before pressing enter
pause

REM Re-run depigifier with any manually matches classes.
set MANUAL_MATCHES=%MIGRATE_DIR%\manual_classes.txt
if exist %MANUAL_MATCHES% (
    echo Running Second Depigifer
    "%JAVA_HOME%/bin/java.exe" -jar %DEPIG_FILE% --oldPG %OLD_MAP% --newPG %NEW_MAP% --out %MIGRATE_DIR%\pig\ --mapping %MANUAL_MATCHES%
    echo.
)

echo Making output
REM Make new MCPConfig folder and copy from previous version.
if not exist %NEW_PATH% (
    mkdir %NEW_PATH%
)
copy /Y %OLD_PATH%\config.json %NEW_PATH%\config.json
copy /Y %OLD_PATH%\suffixes.txt %NEW_PATH%\suffixes.txt
if exist %OLD_PATH%\inject (
    xcopy /E /Y /I %OLD_PATH%\inject %NEW_PATH%\inject
)
echo.

echo Migrating
py MigrateMappings.py %OLD_VERSION% %NEW_VERSION% %MAP_DATA% %OLD_PATH% %NEW_PATH% 2>&1 1>%MIGRATE_DIR%\migrate.log

REM Copy over the new data
REM copy /Y new\conf\joined.tsrg %MCP_CONFIG%\versions\%NEW_VERSION%\joined.tsrg
REM copy /Y new\conf\constructors.txt %MCP_CONFIG%\versions\%NEW_VERSION%\constructors.txt
set NEW_CLASSES=%MIGRATE_DIR%\new_classes.txt
if exist %NEW_CLASSES% (
    echo Base Version: %OLD_VERSION% >>%NEW_CLASSES% 
    copy /Y %NEW_CLASSES% %NEW_PATH%\SNAPSHOT.txt
)

REM pushd new
REM     call py runtime\decompile.py --joined -p -a -n --rg
REM popd

REM py %SCRIPTS%\PostGeneration.py new >new_classes.txt

REM Decompile with SpecialSource, as RG has some naming/generic artifacts that make diffing difficult.
REM pushd new
REM     call py runtime\cleanup.py -f
REM     call py runtime\decompile.py --joined -p -a -n
REM popd