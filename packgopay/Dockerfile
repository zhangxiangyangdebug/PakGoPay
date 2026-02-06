# 使用官方Maven镜像作为构建环境
FROM maven:3.9-eclipse-temurin-21 AS build

# 设置工作目录
WORKDIR /app

# 将项目文件复制到容器中
COPY src ./src
COPY pom.xml .

# 编译项目
RUN mvn -f pom.xml clean package -DskipTests

# 使用官方OpenJDK镜像作为运行时环境
FROM eclipse-temurin:21-jre

# 设置工作目录
WORKDIR /app

# 从构建阶段复制编译后的JAR文件到运行时镜像中
COPY --from=build /app/target/packgopay-0.0.1-SNAPSHOT.jar /app/app.jar

# 暴露端口
EXPOSE 8090

# 指定容器启动时执行的命令
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
