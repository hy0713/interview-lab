# Interview Lab 后端

Java 17 + Spring Boot 3.5.7 + Spring AI 1.1.0。

启动、配置、设计说明和验证方式见项目根目录 [README](../README.md)。

- 本地启动：`mvn spring-boot:run`
- 关键业务测试：`mvn test`
- 打包：`mvn package -DskipTests`
- Swagger：`http://localhost:8080/swagger-ui.html`
- Docker：从项目根目录执行 `docker compose up -d --build`
- [手工验收指南](API_TEST_GUIDE.md)

数据库通过 Flyway 初始化，使用独立的 `ai_interview_v2`，不读取旧版表。
