# MySQL 开发库切换说明

## 本次新增内容

- `application-dev-mysql.yml`：MySQL 开发环境 profile
- `schema-mysql.sql`：MySQL 建表脚本
- `data-mysql.sql`：MySQL 演示数据脚本
- `docker-compose.mysql.yml`：本地 MySQL 开发库启动模板

## 当前默认连接信息

- 地址：`localhost:3306`
- 数据库：`internship_platform`
- 用户名：`root`
- 密码：`123456`

说明：

- JDBC 已开启 `createDatabaseIfNotExist=true`
- 如果本地 MySQL 服务已启动，首次连接可自动创建 `internship_platform`

## 启动 Spring Boot 的 MySQL 模式

```powershell
Set-Location 'd:\CFile\师范生实习记录平台\backend-spring'
java -jar .\target\internship-platform-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev-mysql
```

也可以通过环境变量覆盖：

- `MYSQL_HOST`
- `MYSQL_PORT`
- `MYSQL_DATABASE`
- `MYSQL_USERNAME`
- `MYSQL_PASSWORD`

## Docker 方案

如果你希望单独拉一套开发库，也可以使用：

```powershell
Set-Location 'd:\CFile\师范生实习记录平台\backend-spring'
docker compose -f .\docker-compose.mysql.yml up -d
```

## 当前说明

- 默认 `application.yml` 仍保持 H2 演示库，方便快速联调和演示。
- 启用 `dev-mysql` profile 时，会切换到 MySQL 驱动与 MySQL 专用初始化脚本。
- 当前已具备真实 MySQL 联调条件，可直接启动验证。
