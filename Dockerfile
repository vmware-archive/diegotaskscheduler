FROM ubuntu:14.10
RUN apt-get update
RUN apt-get install -yy openjdk-8-jre
RUN mkdir /app
ADD target/diegoscheduler-0.1.0-SNAPSHOT-standalone.jar /app/server.jar
WORKDIR /app
EXPOSE 8080
ENV PORT=8080
ENTRYPOINT ["java", "-jar", "server.jar"]
