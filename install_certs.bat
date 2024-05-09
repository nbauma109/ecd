@echo off
call keytool -import -file _.nuxeo.org.crt -cacerts -alias nuxeo -storepass changeit -noprompt
call keytool -import -file maven.java.net.crt -cacerts -alias javanet -storepass changeit -noprompt

