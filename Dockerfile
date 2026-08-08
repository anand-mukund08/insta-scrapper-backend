FROM mcr.microsoft.com/playwright/java:v1.49.0-noble

WORKDIR /app

COPY . .

RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

CMD ["sh", "-c", "java -jar target/*.jar"]