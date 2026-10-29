FROM maven:3.9-eclipse-temurin-21

WORKDIR /opt/app
USER ubuntu

ENV MAVEN_CONFIG=/home/ubuntu/.m2

CMD ["mvn", "-B", "-pl", "router", "spring-boot:run", "-Dspring-boot.run.jvmArguments=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5006"]
