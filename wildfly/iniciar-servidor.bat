@echo off
REM Levanta WildFly con la app MediConecta ya desplegada (deployments/mediconecta.war).
REM Requiere JAVA_HOME configurado (ya seteado como variable de usuario en esta PC).
call "%WILDFLY_HOME%\bin\standalone.bat"
