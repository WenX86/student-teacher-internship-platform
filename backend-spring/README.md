# 师范生教育实习全过程管理平台后端

## 当前状态
当前目录已经完成 `Spring Boot` 正式后端骨架和一期、二期核心业务能力，覆盖：
- 登录认证与权限控制
- 学生、教师、学院基础管理
- 实习单位库、指导关系、实习申请
- 核心表单、教师审核、学院归档
- 评价体系、报表中心、风险预警与催办
- 参数配置、日志审计

## 技术栈
- Spring Boot 3.3.5
- Spring Security
- MyBatis-Plus
- H2 / MySQL

## 本地启动
编译：
```powershell
Set-Location 'd:\CFile\师范生实习记录平台\backend-spring'
& 'd:\CFile\师范生实习记录平台\tools\apache-maven-3.9.11\bin\mvn.cmd' -DskipTests package
```

运行开发内存库：
```powershell
java -jar .\target\internship-platform-backend-0.0.1-SNAPSHOT.jar
```

运行 MySQL 开发库：
```powershell
java -jar .\target\internship-platform-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev-mysql
```

运行生产 profile：
```powershell
java -jar .\target\internship-platform-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

## 演示账号
默认密码均为 `123456`：
- 超级管理员：`root`
- 学院管理员：`college01`
- 指导教师：`T1001`
- 学生：`20230001`
- 学生：`20230002`

## 说明
- `application.yml` 默认使用 H2，便于本地快速验证。
- `application-dev-mysql.yml` 用于接 MySQL 开发库。
- `application-prod.yml` 用于生产部署，默认关闭 H2 控制台并收紧 CORS。
- 生产部署与安全建议见 `docs/生产部署与安全加固说明.md`。