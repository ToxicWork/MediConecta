@echo off
REM Levanta WildFly con la app MediConecta ya desplegada (deployments/mediconecta.war).
REM Usa el perfil standalone-full.xml: es el que trae el broker ActiveMQ Artemis (mensajeria JMS).
REM Requiere JAVA_HOME configurado (ya seteado como variable de usuario en esta PC).
call "%WILDFLY_HOME%\bin\standalone.bat" -c standalone-full.xml
