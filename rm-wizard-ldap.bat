rem Copyright (c) 2018 SciForma. All right reserved.

rem Author Raffi JARIAN

@echo off
setlocal

chcp 65001
cls

rem Get arguments
set BATCH_PATH=%cd%
set BATCH_MAIN=pco.schneider.main.MsttRmWizard

rem Set folder path
set ROOT_DIR=%BATCH_PATH%
set LIB_DIR=%ROOT_DIR%/lib
set CONF_DIR=%ROOT_DIR%/conf/prod

rem Open root directory
cd %ROOT_DIR%

rem Set Java arguments with log4j configuration file
set JAVA_ARGS=-showversion
set JAVA_ARGS=%JAVA_ARGS% -Djava.awt.headless=true
set JAVA_ARGS=%JAVA_ARGS% -Dlog4j.overwrite=true
set JAVA_ARGS=%JAVA_ARGS% -Xmx4096m
set JAVA_ARGS=%JAVA_ARGS% -Dfile.encoding=UTF-8
set JAVA_ARGS=%JAVA_ARGS% -Duse_description=true
set JAVA_ARGS=%JAVA_ARGS% -Dlog4j.configurationFile=file://%CONF_DIR%/log4j.properties
set JAVA_ARGS=%JAVA_ARGS% -cp "%LIB_DIR%/*"

rem Launch the API with 2 arguments: psconnect.properties and log4j.properties files paths.
java %JAVA_ARGS% %BATCH_MAIN% ldap %CONF_DIR%
