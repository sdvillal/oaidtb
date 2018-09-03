@echo off

::----------------------------------------------------------------------
:: OAIDTB Script de inicio
::----------------------------------------------------------------------
:: Este es un script genérico para ejecutar cualquiera de las aplicaciones
:: de weka o de la librería oaidtb; para usarlo:
::  -Definir la variable MAIN_CLASS_NAME (posiblemente en otro script)
::  -Llamar a este script.
::  -Si se quiere redirigir la salida a un archivo, 
::   llamar a este script con el nombre de dicho archivo como parámetro.
::----------------------------------------------------------------------

:: ---------------------------------------------------------------------
:: Antes de poder ejecutar nada es necesario especificar
:: La localización de la máquina virtual java
:: ---------------------------------------------------------------------
IF EXIST "%JAVA_HOME%" goto java_home_exists
SET JAVA_HOME=d:\prog\java\j2sdk 1.3

:java_home_exists

:: ---------------------------------------------------------------------
:: Aquí se debe especificar el directorio donde se 
:: ha instalado la aplicación
:: ---------------------------------------------------------------------
SET OAIDTB_HOME=..\..

:: ---------------------------------------------------------------------
:: Si se ha configurado correctamente la variable JAVA_HOME
:: entonces no necesitarás cambiar la configuraciñon
:: de la variable JAVA_EXE
:: ---------------------------------------------------------------------
SET JAVA_EXE=%JAVA_HOME%\bin\java.exe

IF NOT EXIST "%JAVA_EXE%" goto error

:: Indica aquí los parámetros opcionales para la máquina virtual java
::SET JVM_ARGS=-ms16m -mx96m

::Configura el lugar en el que se encuentra la librería weka;
::tener en cuenta que si la librería es una versión igual
::o posterior a la 3.3.1 sólo funcionarán correctamente
::sus aplicaciones gráficas si se ha aplicado el parche que viene con
::esta aplicación
:: if not "%WEKA_HOME%"=="" SET OAIDTB_WEKA_HOME=%WEKA_HOME%
::if "%OAIDTB_WEKA_HOME%"=="" SET OAIDTB_WEKA_HOME=%OAIDTB_HOME%\libs\weka_patched.jar

:: ---------------------------------------------------------------------
::Las siguientes variables sólo es necesario configurarlas 
::en el caso de usar el ClassifierPanel o el AllContainerPanel 
::y de no usar las librerías que acompañan a la aplicación
:: ---------------------------------------------------------------------
::if "%COLT_HOME%"=="" SET COLT_HOME=%OAIDTB_HOME%\libs\colt_only_used_classes.jar
::if "%JFREECHART_HOME%"=="" SET JFREECHART_HOME=%OAIDTB_HOME\libs\jfreechart-0.9.2.jar
::if "%JCOMMON_HOME%"=="" SET JCOMMON_HOME=%OAIDTB_HOME%\libs\jcommon-0.6.4.jar

SET OAIDTB_CLASSPATH=
for %%c in (%OAIDTB_HOME%\libs\*.zip %OAIDTB_HOME%\libs\*.jar) do call _append_.bat %%c
call _append_.bat %OAIDTB_WEKA_HOME%
call _append_.bat %COLT_HOME%
call _append_.bat %JFREECHART_HOME%
call _append_.bat %JCOMMON_HOME%

::SET ALL_ARGS=
::if not "%*"=="*" SET ALL_ARGS = %*
::shift

::---------------------------------------------------------------------
:: Especificar la localización del archivo de propiedades para el GOE
:: No mola--> Cambiarlo en el código fuente
::---------------------------------------------------------------------

IF NOT "%1"=="" goto output_redirected
"%JAVA_EXE%" %JVM_ARGS% -cp "%OAIDTB_CLASSPATH%" -Duser.home=..\cfg %MAIN_CLASS_NAME%
goto end

::---------------------------------------------------------------------
:: En DOS no hay manera de redirigir stderr
:: Se puede hacer de muchas maneras:
::   - Usar redir.exe del DJGPP
::   - Un programita en C de 6 líneas sirve pal caso
::--------redir.c-----------
::#include <stdio.h>
::
:: main (int argc, char ** argv){
::   dup2 (fileno (stdout), fileno (stderr));
::   execvp (argv[1], argv + 1);
:: }
::----------- end of redir.c ----------
::Usage: redir prog arg1 arg2 ... argn
::
::   - etc.
::---------------------------------------------------------------------
:output_redirected
"%JAVA_EXE%" %JVM_ARGS% -cp "%OAIDTB_CLASSPATH%" -Duser.home=..\cfg %MAIN_CLASS_NAME% >%1
goto end

:error
echo ---------------------------------------------------------------------
echo ERROR: No se puede iniciar la máquina virtual de java
echo Por favor, especifica la variable JAVA_HOME en este archivo batch
echo ---------------------------------------------------------------------
pause

:end