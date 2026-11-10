# --- Stage 1: Build với Gradle (Đã giới hạn RAM) ---
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# Copy các file cấu hình
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src

# Giới hạn RAM tối đa 384MB cho Gradle để tránh sập RAM Render
RUN gradle bootJar -x test --no-daemon -Dorg.gradle.jvmargs="-Xmx384m"

# --- Stage 2: Chạy ứng dụng ---
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

ENV PORT=8080
EXPOSE 8080

CMD ["java", "-Xmx384m", "-jar", "app.jar"]