@echo off
call keytool -import -file crt/maven.java.net.crt -cacerts -alias javanet -storepass changeit -noprompt

