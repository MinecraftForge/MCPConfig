@echo off

REM Requires python3+, and python wget installed https://pypi.org/project/wget/

REM Update these as nessasary
set MAP_TOY=1.0.6
set DEPIG=1.0.4

REM Basic paths, shouldn't need changing
set MC_ROOT=%APPDATA%\.minecraft
set MCP_CONFIG=..
set MAP_DATA=output
set BIN_DIR=bin

REM Read arguments
set OLD_VERSION=%1
set NEW_VERSION=%2

set MIGRATE_DIR=%MAP_DATA%\%OLD_VERSION%_to_%NEW_VERSION%

echo Starting %OLD_VERSION% -^> %NEW_VERSION%
echo MC Root : %MC_ROOT%
echo MCP Cfg : %MCP_CONFIG%
echo Data    : %MAP_DATA%
echo Bin     : %BIN_DIR%
echo Migrate : %MIGRATE_DIR%
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

java -jar "%MAP_TOY_FILE%" --libs --output %MAP_DATA%\ --mc %MC_ROOT%\ --version %OLD_VERSION% --version %NEW_VERSION%

set OLD_MAP=%MAP_DATA%\%OLD_VERSION%\client.txt
if not exist "%OLD_MAP%" (
    echo Missing Old Map: %OLD_MAP%
    GOTO :EOF
)
echo Old Map : %OLD_MAP%
set NEW_MAP=%MAP_DATA%\%NEW_VERSION%\client.txt
if not exist "%NEW_MAP%" (
    echo Missing Old Map: %NEW_MAP%
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

set DEPIG_FILE=%BIN_DIR%\MappingToy-%DEPIG%-fatjar.jar
if not exist %DEPIG_FILE% (
    echo Downloading Depigifier: %DEPIG%
    python -m wget -o "%DEPIG_FILE%" "https://files.minecraftforge.net/maven/net/minecraftforge/depigifier/%DEPIG%/depigifier-%DEPIG%-fatjar.jar"
    echo.
)
if not exist %DEPIG_FILE% GOTO :EOF

echo Running First Depigifer
java -jar %DEPIG_FILE% --oldPG %OLD_MAP% --newPG %NEW_MAP% --out %MIGRATE_DIR%\pig\

echo Fix any suggestions before pressing enter
pause

REM Re-run depigifier with any manually matches classes.
set MANUAL_MATCHES=%MIGRATE_DIR%\manual_classes.txt
if exist %MANUAL_MATCHES% (
    echo Running Second Depigifer
    java -jar %DEPIG_FILE% --oldPG %OLD_MAP% --newPG %NEW_MAP% --out %MIGRATE_DIR%\pig\ --mapping %MANUAL_MATCHES%
    echo.
)

echo Making output
REM Make new MCPConfig folder and copy from previous version.
if not exist %MCP_CONFIG%\versions\%NEW_VERSION%\ (
    mkdir %MCP_CONFIG%\versions\%NEW_VERSION%\
)
copy /Y %MCP_CONFIG%\versions\%OLD_VERSION%\config.json %MCP_CONFIG%\versions\%NEW_VERSION%\config.json
copy /Y %MCP_CONFIG%\versions\%OLD_VERSION%\suffixes.txt %MCP_CONFIG%\versions\%NEW_VERSION%\suffixes.txt
echo.

echo Migrating
py MigrateMappings.py %MCP_CONFIG% %OLD_VERSION% %NEW_VERSION% %MAP_DATA% 2>&1 1>%MIGRATE_DIR%\migrate.log

REM Copy over the new data
REM copy /Y new\conf\joined.tsrg %MCP_CONFIG%\versions\%NEW_VERSION%\joined.tsrg
REM copy /Y new\conf\constructors.txt %MCP_CONFIG%\versions\%NEW_VERSION%\constructors.txt
set NEW_CLASSES=%MIGRATE_DIR%\new_classes.txt
if exist %NEW_CLASSES% (
    echo Base Version: %OLD_VERSION% >>%NEW_CLASSES%
    copy /Y %NEW_CLASSES% %MCP_CONFIG%\versions\%NEW_VERSION%\SNAPSHOT.txt
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