FROM maven:3.9-eclipse-temurin-21

WORKDIR /opt/app
USER ubuntu

ENV MAVEN_CONFIG=/home/ubuntu/.m2
# Maven only compiles and then waits on the forked app, so keep its own JVM small
ENV MAVEN_OPTS="-Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"

# The periodic GC hands unused heap back to the OS while the app is idle
CMD ["mvn", "-B", "-pl", "router", "spring-boot:run", "-Dspring-boot.run.jvmArguments=-XX:G1PeriodicGCInterval=60000 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/opt/app/logs -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5006"]
