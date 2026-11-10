# --- Stage 1: Build dự án với Gradle ---
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# Copy các file cấu hình để cache dependencies
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# Copy mã nguồn và đóng gói file JAR (bỏ qua bước test để deploy nhanh)
COPY src ./src
RUN gradle bootJar -x test

# --- Stage 2: Chạy ứng dụng với JRE gọn nhẹ ---
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy file JAR đã tạo từ Stage 1
# Lưu ý: Lấy file executable jar, bỏ qua file -plain.jar
COPY --from=build /app/build/libs/*.jar app.jar

# Khai báo cổng động từ môi trường của Render
ENV PORT=8080
EXPOSE 8080

CMD ["java", "-jar", "app.jar"]