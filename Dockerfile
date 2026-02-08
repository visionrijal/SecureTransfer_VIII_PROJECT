FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY data ./data
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jdk

RUN apt-get update && apt-get install -y \
    libxrender1 \
    libxtst6 \
    libxi6 \
    libgl1 \
    libgtk-3-0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /app/target/secure-file-transfer-1.0.0.jar ./secure-file-transfer-1.0.0.jar

EXPOSE 8080 8081

ENV SERVER_PORT=8080
ENV DATABASE_URL=jdbc:h2:mem:instance1
ENV DISPLAY=${DISPLAY}

CMD java -jar \
    -Dserver.port=$SERVER_PORT \
    -Dspring.datasource.url=$DATABASE_URL \
    secure-file-transfer-1.0.0.jar

