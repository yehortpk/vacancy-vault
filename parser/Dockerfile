FROM mcr.microsoft.com/playwright/java:v1.53.0

WORKDIR /opt/app
USER ubuntu

ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
# Maven only compiles and then waits on the forked app, so keep its own JVM small
ENV MAVEN_OPTS="-Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
# No fixed heap: the browser pool can size itself from the host, so the heap needs room to follow.
# The periodic GC hands unused heap back to the OS while the app is idle
ENV JAVA_OPTS="-XX:G1PeriodicGCInterval=60000 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/opt/app/logs -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

CMD mvn -B -pl parser spring-boot:run -Dspring-boot.run.workingDirectory=/opt/app -Dspring-boot.run.jvmArguments="$JAVA_OPTS"
