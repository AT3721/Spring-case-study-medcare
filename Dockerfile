# --- Stage 1: Build dự án ---
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# Copy toàn bộ dự án vào container (đảm bảo không bị thiếu file)
COPY . .

# Chạy lệnh build
RUN gradle bootJar -x test --no-daemon -Dorg.gradle.jvmargs="-Xmx384m"

# --- Stage 2: Chạy ứng dụng ---
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

ENV PORT=8080
EXPOSE 8080

CMD ["java", "-Xmx384m", "-jar", "app.jar"]