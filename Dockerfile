# ===== 构建阶段 =====
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先只拷 pom，利用镜像层缓存把依赖下好；改代码时不必重新下依赖
COPY pom.xml .
RUN mvn -B -ntp -q dependency:go-offline

COPY src ./src

# 仓库里不提交 application-local.yml（含口令），
# 构建时用模板生成一份，占位符会在运行时由环境变量填充
RUN cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml

RUN mvn -B -ntp -q package -DskipTests

# ===== 运行阶段 =====
FROM eclipse-temurin:17-jre
WORKDIR /app

# 时区与字符集：日志和报表时间要对得上
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    SPRING_PROFILES_ACTIVE=local \
    JAVA_OPTS="-Xms256m -Xmx512m"

COPY --from=build /app/target/kb-platform-1.0.0.jar app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
