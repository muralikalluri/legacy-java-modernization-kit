# Deliberately insecure for demonstration. Do not deploy.
FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package

FROM tomcat:9.0-jdk8-temurin
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /src/target/portal.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
