FROM mcr.microsoft.com/playwright/java:v1.53.0

WORKDIR /opt/app
USER ubuntu

ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
# A fixed heap rather than a share of memory, since Chromium runs beside the JVM in this container
ENV JAVA_OPTS="-Xmx512m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

CMD mvn -B -pl parser spring-boot:run -Dspring-boot.run.workingDirectory=/opt/app -Dspring-boot.run.jvmArguments="$JAVA_OPTS"
