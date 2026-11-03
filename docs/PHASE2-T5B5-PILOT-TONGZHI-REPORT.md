# Step 5b / 试点第一批：`Tongzhi` → `Notification`（交付报告）

> **状态**：✅ 完成（**未推送**，按规则 §9.1 等你授权）
> **范围**：仅在审阅锁定的路线内 —— 补完 ①②④ 后，用**最小模块**验证完整的重命名链
> **零业务变更**：API 路径 / 表名 / Bean 名 / 前端 / 数据库 全部未动

---

## 1. 改名映射表（执行前先给出，本表即执行依据）

| # | 旧 | 新 | 必须**保持不动**的关联项 |
| :-: | --- | --- | --- |
| 1 | `entity/TongzhiEntity.java` | `NotificationEntity.java` | `@TableName("notification")`；泛型 `<T>`；`toString()` 前缀一并更新 |
| 2 | `entity/view/TongzhiView.java` | `NotificationView.java` | `@TableName("notification")`；`extends` 同步 |
| 3 | `service/TongzhiService.java` | `NotificationService.java` | — |
| 4 | `service/impl/TongzhiServiceImpl.java` | `NotificationServiceImpl.java` | ⚠️ **`@Service("tongzhiService")`** |
| 5 | `dao/TongzhiDao.java` | `NotificationDao.java` | — |
| 6 | `controller/TongzhiController.java` | `NotificationController.java` | ⚠️ **`@RequestMapping("/tongzhi")`** |
| 7 | `mapper/TongzhiDao.xml` | `NotificationDao.xml` | `namespace` 跟类名走；**`FROM notification`（表名）不动** |
| 8 | — | — | ⚠️ **`DICT_CODE` 键**（见 §3）—— 键必须改，**字典编码值 `notification_category` 不改** |

**刻意不动**（且已在验证中确认未被误改）：
`@RequestMapping("/tongzhi")` · `@Service("tongzhiService")` · `@TableName("notification")` ·
测试中的 API 字符串（`/tongzhi/info/1`、`/tongzhi/page`）· 局部标识符（`tongzhiService`、`tongzhiEntity`、`tongzhiList`）·
日志格式串（`"… tongzhi:{}"`）· 前端模块标识符 `dictionaryTongzhi`（**前端自有标识符，非类名引用**）

---

## 2. 实施方式：**逐文件单次 `replace_all`（大小写敏感）**

本次试点确定的可复制手法：每个文件只做一次 `Tongzhi` → `Notification` 替换（区分大小写）。

**因此有一个强性质**：小写的 `tongzhi`（API 路径、表名、Bean 名、局部变量、日志串）**在物理上不可能被误改** ——
不是「我小心没改」，而是「替换规则匹配不到」。实测核对：`RequestMapping("/tongzhi")`=1 ✓、`@Service("tongzhiService")`=1 ✓、`@TableName("notification")`=2 ✓。

改动量（与预估一致）：控制器 22 处类型引用、实体 4 处（含 `toString`）、View 4 处、Impl 6 处、Dao 4 处、Service 2 处、XML 2 处。

---

## 3. 🔴 试点最重要的产出：发现第 4 类耦合（①②④ 都未覆盖）

### 3.1 问题

`DictionaryServiceImpl.dictionaryConvert()` 在**运行期**按下述规则拼 key：

```java
fieldOwner.put(f.getName(), c.getSimpleName());          // 记录字段的【声明类简单名】
...
if (owner.endsWith("Entity")) owner = owner.substring(0, -6);   // "TongzhiEntity" → "Tongzhi"
String dictCode = DICT_CODE.get(owner + "." + fieldName);       // 查静态映射表
```

`DICT_CODE` 是**字符串表**（`"Tongzhi.category" → "notification_category"`）。

**所以：改类名而不改字符串 → 字典翻译静默失效**：
接口仍返回 `200 + code=0`，只有 `categoryLabel` 悄悄变成 `null` ✗。
这**既不是编译错误，也不能被 ①（引用计数）②（Bean 名）④（XML）发现** —— 因为它是一处**没有「引用」外观的字符串**。

### 3.2 全仓扫描：这是**唯一**一处

| 检查项 | 全仓命中 |
| --- | :-: |
| `getSimpleName()` | **1**（`DictionaryServiceImpl:91`）|
| `endsWith("Entity")` / `substring(0, owner…)` | **1 / 1**（同文件 L102-103）|
| `replace("Entity"` / `replace("View"` | **0** |
| `getAnnotation(` / `TableName.class` / `TableInfoHelper` | **0 / 0 / 0** |

→ 该类耦合**有限且已封堵**：修复 1 处即可，且已加入永久断言（§5）。

### 3.3 后续每个模块的字典键清单（**批量推进时逐条核对**）

| 模块 | 需同步的 `DICT_CODE` 键 | 模块分类 |
| --- | --- | --- |
| `Yonghu` | `Yonghu.gender` | B |
| `Exampaper` | `Exampaper.status` | B |
| `Examquestion` | `Examquestion.questionType` | B |
| `Jiankangzhishi` | `Jiankangzhishi.category` | B |
| `XinlilaoshiLiuyan` / `Collection` / `Order` | `XinlilaoshiOrder.timeSlot` / `.status`、`XinlilaoshiCollection.favoriteType` | B |
| **`Xinlilaoshi`** | `Xinlilaoshi.gender` | **C（暂缓）** |

---

## 4. 验证矩阵

| 层 | 手段 | 结果 |
| --- | --- | --- |
| **静态** | 旧类名残留（java+mapper+test，区分大小写） | **0** ✓ |
| **静态** | 四条硬约束逐条计数 | 1 / 1 / 2 / 1（旧键残留 0）✓ |
| **构建** | `mvn -B clean package` | **BUILD SUCCESS** ✓ |
| **回归** | `IdentityAuthorizationRegressionTest` 27 + `Phase2CoreRegressionTest` 15 | **42/42** ✓ |
| **端到端（真实容器）** | `/tongzhi/page` → `code=0 total=5`，`categoryLabel` 与字典值**逐字符一致 5/5** | ✅ |
| **端到端** | `/tongzhi/info/1` → 逐字符一致 | ✅ |
| **对照组** | `/jiankangzhishi/page`（**未改名**模块）→ 3/3 | ✅ 证明机制本身正常 |
| **数据** | 测试/运行前后 `auth_token` 行数与留言归属快照 | **完全一致** ✓ |
| **反向对照** | 把字典键改回 `"Tongzhi.category"` → 新用例**失败**并打印完整 `DICT_CODE` 与缺失键 `Notification.category` | ✅ 断言有效 |

---

## 5. 新增的永久防线（用例 10）

固化不变式：**`DICT_CODE` 的键必须等于运行期推导值**

```text
key = 声明类的简单名（去 "Entity" 后缀） + "." + 字段名
      NotificationEntity.category  →  "Notification.category"
```

**为什么不去直接断言接口的 `categoryLabel`**（我一开始就是这么写的，实测失败后纠正）：
`dictionaryMap` 由 `DictionaryServletContextListener`（`@WebListener`）写入 `ServletContext`，
而 `@SpringBootTest`(MOCK) **不初始化 WebListener** → 直接断言会**因环境而假失败** ✗。
故测试固化为「key 契约」+ 端到端行为交给真实容器（§4）—— 两者互补。

---

## 6. 过程自我纠正（2 处测量错误，**均非代码问题**）

| # | 错误 | 症状 | 纠正 |
| :-: | --- | --- | --- |
| 1 | **先改代码、后加基线用例** | 新用例首次运行即失败，**无法判定是真断链还是环境问题** | 追查后确认是 MockMvc 无 WebListener → 改为环境无关的 key 断言，并补真实容器端到端 |
| 2 | **PowerShell 对无 `charset` 头的 JSON 按 Latin-1 解码** | 逐字符比对全部 MISMATCH，看似中文全坏 | hex 显示 `E9 80 9A…` = **UTF-8 字节被按 Latin-1 解**；**决定性证据是未改名的对照组出现同样伪影** → 改用 `curl` + 显式 UTF-8 字节比对 → 5/5 ✓ |

> 教训固化：**「对照用例」是区分「我真改坏了」与「我测错了」的最有效工具** —— 本次两处纠正都靠它。

---

## 7. 已知瑕疵（如实登记）

* **提交信息不准确**：本地提交 `adc2cd3` 实际包含**代码 + 测试共 10 文件**，但提交信息只描述了「测试」部分
  （我的命令转义出错导致代码提交失败、随后测试提交把全部改动一并提交）。
  按规则**未擅自 `--amend` / `reset`** 改写历史 → **待你裁定**：是否授权修正提交信息（未推送）。

---

## 8. 结论与下一步

* 试点证明：**B 类最小模块可以安全改名** —— 42/42 + 端到端逐字符一致 + 库不变 + 反向对照有效 ✓
* **手法可复制**：文件名 `git mv` → 每文件单次大小写敏感替换 → 同步 XML `namespace`/`resultType` → **检查 `DICT_CODE`** → 测试 + 真实容器验证
* **批量推进前建议**：按 §3.3 清单逐模块核对字典键；`Xinlilaoshi`（C 类）继续暂缓
* 未做的事（守边界）：`Jiankangzhishi`（并列最小）**未动** —— 是否推进由你裁定
