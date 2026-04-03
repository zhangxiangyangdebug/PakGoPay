# 使用官方OpenJDK镜像作为运行时环境
FROM eclipse-temurin:21-jre

# 设置工作目录
WORKDIR /app

# 安装 OCR 依赖（Telegram 图片识别订单号）
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

# 直接使用本地已编译的JAR
COPY target/packgopay-0.0.1-SNAPSHOT.jar /app/app.jar

# 暴露端口
EXPOSE 8090

# 指定容器启动时执行的命令
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

