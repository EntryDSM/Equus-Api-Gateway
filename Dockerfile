FROM openjdk:17
ENV TZ=Asia/Seoul
COPY build/libs/Equus-Api-Gateway-0.0.1-SNAPSHOT.jar app.jar

COPY /datadog/dd-java-agent.jar /usr/agent/dd-java-agent.jar

ENTRYPOINT ["java","-javaagent:/usr/agent/dd-java-agent.jar", "-Ddd.agent.host=localhost", "-Ddd.profiling.enabled=true","-XX:FlightRecorderOptions=stackdepth=256", "-Ddd.logs.injection=true", "-Ddd.service=equus-api-gateway", "-Ddd.env=prod", "-Dspring.profiles.active=production", "-jar", "/app.jar"]
