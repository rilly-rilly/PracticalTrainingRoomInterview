# 后端启动与数据库说明

## 启动后端

```bash
cd backend
mvn spring-boot:run
```

默认 profile = **default**（H2 内存数据库），启动后访问 `http://localhost:8080/api/public/home` 能看到 JSON 响应。前端 dev server（`npm run dev`）会把请求转到 8080。

## 切换到 MySQL

### 1. 创建数据库并导入 DDL

```sql
CREATE DATABASE IF NOT EXISTS trainee_selection DEFAULT CHARACTER SET utf8mb4;
USE trainee_selection;
SOURCE sql/schema-mysql.sql;
SOURCE sql/data-mysql.sql;
```

或 PowerShell 一行：

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -uroot -p < sql\schema-mysql.sql
```

### 2. 激活 MySQL profile

编辑 `src/main/resources/application-mysql.yml`，填入你的 MySQL 连接串、用户名、密码，然后启动：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

## H2 与 MySQL 两套 DDL 的差异

| 项 | H2（开发） | MySQL（正式） |
| --- | --- | --- |
| 引擎 | 内存/文件 | InnoDB |
| ENUM | 用 `CHECK (col IN (...))` 等价 | MySQL 8 ENUM 原生 |
| TINYINT UNSIGNED | 用 INTEGER | 原生支持 |
| JSON 字段 | 用 VARCHAR 存 JSON 文本 | MySQL 原生 JSON |
| CHECK 约束 | 宽松，开发期够用 | 严格按 docs/02 |
| 种子数据 | `schema-h2.sql` 末尾直接 VALUES | `sql/data-mysql.sql` 单独文件 |

两套 DDL 都严格按 docs/02 的字段命名（snake_case），实体层用 MyBatis-Plus 的下划线转驼峰自动映射（`map-underscore-to-camel-case=true`），Java 实体用 `Long`、`LocalDateTime`、枚举类。

## 密码哈希说明

种子数据的 BCrypt 哈希值固定：

| 账号 | 原始密码 | BCrypt 哈希（固定） |
| --- | --- | --- |
| admin / admin | `admin123` | `$2a$10$...`（见 data 文件） |
| 导师 T1001~T1005 | `Teach@2026` | `$2a$10$...` |
| 学生 S2026001~S2026009 | `Stu@2026` | `$2a$10$...` |

BCryptPasswordEncoder 能正确验证这些哈希值。如果用 Online BCrypt Generator 生成自己的哈希，直接替换对应 INSERT 行的 password_hash 即可。

## 接口契约

全部 REST 路径、方法、统一响应体与前端 mock 完全一致，见 docs/03-backend-api.md 与 frontend/src/api/index.js。后端 Controller 路径一一对齐，请求体和响应结构不变——前端换成 HTTP 调用后不需要改业务代码，只把 mock adapter 去掉即可。
