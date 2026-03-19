# 师范生教育实习全过程管理平台后端（Spring Boot 一期）

## 当前状态

当前目录已完成一期正式后端迁移骨架，并落地了以下能力：

- 登录认证与权限控制
- 学生、教师、学院基础管理
- 实习单位信息库管理
- 实习申请与指导关系建立
- 任课实习与班主任实习核心表单
- 教师审核与学院归档
- 基础统计与消息提醒

## 技术栈

- Spring Boot 3.3.5
- Spring Security
- MyBatis-Plus
- H2（默认开发演示库）
- MySQL（后续生产切换）

## 目录说明

- `src/main/java/com/internship/platform`：后端主代码
- `src/main/resources/schema.sql`：一期数据库结构脚本
- `src/main/resources/data.sql`：一期演示种子数据
- `pom.xml`：Maven 工程配置

## 启动方式

### 1. 编译

```powershell
Set-Location 'd:\CFile\师范生实习记录平台\backend-spring'
& 'd:\CFile\师范生实习记录平台\tools\apache-maven-3.9.11\bin\mvn.cmd' -DskipTests package
```

### 2. 运行

```powershell
Set-Location 'd:\CFile\师范生实习记录平台\backend-spring'
java -jar .\target\internship-platform-backend-0.0.1-SNAPSHOT.jar
```

### 3. 健康检查

```powershell
curl.exe http://localhost:8080/api/health
```

## 演示账号

默认密码均为 `123456`。

- 超级管理员：`root`
- 学院管理员：`college01`
- 指导教师：`T1001`
- 学生：`20230001`
- 学生：`20230002`

## 当前说明

- 当前默认使用 H2 内存数据库，便于快速验证一期流程。
- 代码结构已经按 Spring Boot 正式后端方向落地，可继续切换到 MySQL。
- 当前前端尚未切换到 Spring Boot 接口，下一步建议补统一接口文档并完成前端联调。
