FROM mcr.microsoft.com/playwright/java:v1.53.0

WORKDIR /opt/app
USER ubuntu

ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
ENV JAVA_OPTS="-XX:MaxRAMPercentage=35"

CMD mvn -B -pl parser spring-boot:run -Dspring-boot.run.workingDirectory=/opt/app -Dspring-boot.run.jvmArguments="$JAVA_OPTS"
