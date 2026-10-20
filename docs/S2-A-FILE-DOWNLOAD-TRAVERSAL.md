# S2-A —— 文件下载路径穿越修复（A1 · 纯安全最小修）

> 日期：2026-10-20 ｜ 基线：`44c0177`（LEAD=0）
> 状态：**已实现 · 🚧 未提交** ✗ ｜ 改动 **1 文件**（`src/main/java/com/controller/FileController.java`）
> 范围裁定：**只堵 P0** —— 不恢复下载功能、不修上传目录漂移、不删接口、不改 `/file/upload`、不改状态码语义 ✓

---

## 1. 修复前基线（实测）

| 请求 | HTTP | 字节 | 结论 |
| --- | :-: | :-: | --- |
| `?fileName=../pom.xml` | **201** | **5576** | 读到项目 `pom.xml` ✗ |
| `?fileName=..%2Fpom.xml` | **201** | **5576** | 编码绕过同样有效 ✗ |
| `?fileName=config1.jpg`（真实存在）| **500** | 0 | **合法下载本已损坏** ⚠️ |
| 任意不存在文件 | 500 | 0 | 兜底 |

根因（已定位）：`ResourceUtils.getURL("classpath:static")` 解析失败 → 回退 `new File("")`（工作目录）→ 基目录 = `<CWD>/upload`（原本不存在，`mkdirs` 创建为空目录）；真实图片在 `static/upload`（19 个）⇒ **该端点当时只剩穿越可用**。

调用面：旧前端 **0** · 管理端（含 `dist`）**仅 `/file/upload`** · 新前端 **0** ⇒ `/file/download` **无仓库内调用方**。

---

## 2. 修改内容（A1）

```java
// S2-A（A1）：路径边界约束 —— 核心是 canonical 包含校验，字符串过滤仅作辅助
if (fileName == null || fileName.trim().isEmpty() || fileName.indexOf('\0') >= 0) {
    return new ResponseEntity<byte[]>(HttpStatus.FORBIDDEN);
}
File base = upload.getCanonicalFile();
File file = new File(base, fileName).getCanonicalFile();
if (!file.getPath().equals(base.getPath())
        && !file.getPath().startsWith(base.getPath() + File.separator)) {
    return new ResponseEntity<byte[]>(HttpStatus.FORBIDDEN);
}
```

- **核心**：`getCanonicalFile()` 消解 `..`、`.`、分隔符、**符号链接**后再做前缀包含判断
- **辅助**：空文件名 / NUL 直接拒绝
- **未改**：成功语义仍 **201**、未命中仍走兜底 **500**、`/file/upload` 一行未动

---

## 3. 修复后回归矩阵（全部实测）

| 输入 | HTTP | 字节 | 判定 |
| --- | :-: | :-: | --- |
| `../pom.xml` | **403** | 0 | ✅ 已拦 |
| `..%2Fpom.xml` | **403** | 0 | ✅ 已拦 |
| `..%5Cpom.xml` | **403** | 0 | ✅ 已拦 |
| `%2e%2e%2Fpom.xml` | **403** | 0 | ✅ 已拦 |
| `%2e%2e%5Cpom.xml` | **403** | 0 | ✅ 已拦 |
| （空 `fileName`） | **403** | 0 | ✅ 已拦 |
| `..\pom.xml`（原始反斜杠）| 400 | 431 | ✅ 容器层拒绝；**响应体不含文件内容** |
| `C:\Windows\win.ini` | 400 | 431 | ✅ 同上（实测无 `[fonts]` 内容）|
| `....//pom.xml` | 500 | 0 | ✅ 规范化后仍在基目录内 → 不存在（非绕过）|
| `config1.jpg` | **500** | 0 | ✅ **与修复前一致**（未改善、未恶化）|
| `nope.jpg` | 500 | 0 | ✅ 不变 |
| **基目录内探针文件** `s2a-probe.txt` | **201** | **12** | ✅ **未过度拦截**（合法文件仍可下）|

> 探针文件为临时创建，测试后已删除 ✓；探测产生的空目录 `<项目根>/upload/` 已清理 ✓。

---

## 4. 其它回归

| 项 | 结果 |
| --- | :-: |
| `mvn -B test` | **47 / 47** · failures 0 · errors 0 · **BUILD SUCCESS** ✓ |
| 静态资源 `/upload/config1.jpg` | **200** ✓（未受影响）|
| `/assessmentPaper/list` | **200** ✓ |
| Vite `5173/assessment` | **200** ✓ |
| 前端代码改动 | **0** ✓ |
| DB 业务数据 | **未变**（paper 2 / question 6）✓ |

---

## 5. 部署注意事项（本轮实际踩到）

| 事实 | 说明 |
| --- | --- |
| 后端以 **`java -jar target/xinlijiankangxitong-0.0.1-SNAPSHOT.jar`** 运行（pid 9612）| 改 Java 必须重新打包 + 重启 |
| **Windows 文件锁**：jar 运行时 `mvn package` 会在 `spring-boot:repackage` 阶段失败（`Unable to rename …jar → …jar.original`）| ⇒ **必须先停进程再打包**，否则产物无 `Main-Class`（`java -jar` 报“没有主清单属性”）|

---

## 6. 副作用登记（测试卫生，**不并入本批**）

| 事实 | 说明 |
| --- | --- |
| `mvn test` 会遗留 `auth_token` 行 | 本轮实测：运行后出现 `id=211 · a1 · student · 14:27:20`；**已清理** ✓（`delete where id>3`）|
| 归类 | `DEF-REGRESSION-TEST-DATA-MUTATION`（**TBD**）—— 与 S2 安全主线无关，属独立 `TEST-DATA-ISOLATION` 任务 |

---

## 7. 未做 / 明确不在本批 ✗

| 项 | 状态 |
| --- | --- |
| 恢复 `/file/download` 正常下载（500 → 200/201）| ❌ 不做（`DEF-UPLOAD-PATH-DRIFT`，独立问题）|
| 修 `classpath:static/upload` 基目录 | ❌ 不做 |
| 修 `/file/upload`（匿名/类型白名单/落点）| ❌ 不做（`SEC-FILE-UPLOAD-ANON`，另批）|
| 删除 `/file/download` | ❌ 不做（虽无调用方，仍需独立裁定）|
| 改 HTTP 状态码语义（201/500）| ❌ 不做 |
| 其它 S2-B/C/D/E 项 | ❌ 不做 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | S2-A（A1）完成：canonical 包含校验 + 空名/NUL 拒绝；**5 种穿越变体全部 403**、容器层 2 种 400 且无内容、`....//` 为 500-未命中；**合法基线未恶化**（`config1.jpg` 仍 500）；**基目录内文件仍 201**；**47/47** ✓；记录 Windows 文件锁部署坑与 `mvn test` 遗留 token（已清理）；**未提交** ✗ |
