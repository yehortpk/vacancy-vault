FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /opt/app
COPY parser/pom.xml /opt/app/parser/pom.xml
COPY pom.xml /opt/app/pom.xml

COPY parser/src /opt/app/parser/src
COPY router/pom.xml /opt/app/router/pom.xml
COPY subscriber-bot/pom.xml /opt/app/subscriber-bot/pom.xml

RUN --mount=type=cache,target=/root/.m2 mvn clean install -pl parser -DskipTests


FROM mcr.microsoft.com/playwright/java:v1.53.0 AS runner

WORKDIR /app
ENV PATH="/usr/local/bin:${PATH}"

ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

COPY --from=builder /opt/app/parser/target/parser-0.0.1-SNAPSHOT.jar .

# Chromium runs beside the JVM in this container, so the heap is capped well below the container
# limit to leave the browsers room. Shell form, so JAVA_OPTS is expanded at startup.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=35"

CMD java $JAVA_OPTS -jar /app/parser-0.0.1-SNAPSHOT.jar