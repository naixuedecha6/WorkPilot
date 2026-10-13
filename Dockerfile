FROM eclipse-temurin:21-jre

WORKDIR /app
COPY target/workpilot-0.1.0.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV OPENAI_API_KEY=""
ENV OPENAI_API_BASE="https://api.deepseek.com"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
