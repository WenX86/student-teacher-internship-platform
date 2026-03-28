# 师范生教育实习平台后端

## 当前状态
- 已完成 Spring Boot 一期正式后端迁移
- 默认使用 H2 内存库，便于本地联调
- 已支持学生、教师、学院管理员、超级管理员四类角色接口

## 技术栈
- Spring Boot 3.3.5
- Spring Security
- MyBatis-Plus
- H2 / MySQL

## 本地启动

### 推荐方式
在当前仓库路径包含中文目录时，`mvn spring-boot:run` 在 Windows 下可能会因为 Spring Boot Maven 插件的 classpath argfile 机制启动失败。

推荐直接使用项目脚本：

```powershell
Set-Location 'D:\CFile\师范生实习记录平台\backend-spring'
.\start-dev.ps1 -Detached
```

脚本内部仍然执行 `mvn spring-boot:run -DskipTests`，只是会自动切换到 ASCII 路径的 junction 工作目录，避免中文路径触发本地启动异常。

停止后端：

```powershell
.\stop-dev.ps1
```

### 直接在当前窗口运行
```powershell
Set-Location 'D:\CFile\师范生实习记录平台\backend-spring'
.\start-dev.ps1
```

### 直接打包运行
```powershell
Set-Location 'D:\CFile\师范生实习记录平台\backend-spring'
& 'D:\CFile\师范生实习记录平台\tools\apache-maven-3.9.11\bin\mvn.cmd' -DskipTests package
java -jar .\target\internship-platform-backend-0.0.1-SNAPSHOT.jar
```

### MySQL 开发库
```powershell
.\start-dev.ps1 -UseMySql -Detached
```

或：

```powershell
java -jar .\target\internship-platform-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev-mysql
```

## 演示账号
默认密码均为 `123456`：

- 超级管理员：`root`
- 学院管理员：`college01`
- 教师：`T1001`
- 教师：`T1002`
- 学生：`20230001`
- 学生：`20230002`

## 说明
- `application.yml` 默认使用 H2
- `application-dev-mysql.yml` 用于 MySQL 开发联调
- `application-prod.yml` 用于生产环境配置
- 批量导入、附件上传、系统参数、表单模板等能力均已接入当前后端
