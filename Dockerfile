FROM mcr.microsoft.com/playwright/java:v1.49.0-noble

WORKDIR /app

COPY . .

RUN chmod +x mvnw

RUN ./mvnw clean package -DskipTests

RUN cp target/*.jar app.jar

EXPOSE 10000

CMD ["java", "-jar", "app.jar"]