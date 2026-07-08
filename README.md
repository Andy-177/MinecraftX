# MinecraftX

一个 Paper/Bukkit 插件，为 Minecraft 服务器提供 **Lua 脚本系统** 和 **模块化扩展框架**，并实现了完整的 **Java-Lua 桥接层**，让 Lua 脚本能够无缝使用 Java 全部特性。

---

## 特性

### 🎮 Lua 脚本引擎
- 在 `MinecraftX/script/` 目录下放置 `.lua` 文件，通过 `/script run <文件名>` 执行
- 支持 Bukkit/Paper API 的全部访问权限
- 事件监听、消息广播、定时任务、Adventure API 等开箱即用

### 📦 模块系统
- 在 `MinecraftX/module/` 目录下按模块组织，每个模块独立隔离
- `manifest.yml` 声明模块元数据（名称、版本、作者、依赖、冲突）
- 支持 `onEnable`/`onDisable` 生命周期
- `export()`/`import()` 跨模块函数共享
- 自动依赖解析 + 循环依赖检测
- 包管理器 `/mpm` 支持安装/卸载/打包

### ☕ Java 特性桥接（全新）
Lua 脚本可以直接使用以下 Java 全部特性：

| 特性 | 说明 |
|---|---|
| **动态代理 (LuaProxy)** | `createProxy()` — Lua 表实现任意 Java 接口，方法调用自动路由到 Lua 函数 |
| **Lambda/SAM** | `createSAM()` `wrapLambda()` — Lua 函数包装为 Java 函数式接口（Runnable/Consumer/Supplier 等） |
| **Java 注解** | `getAnnotations()` `getAnnotation()` `hasAnnotation()` — 读取类/方法/字段上的注解 |
| **异步并发** | `async()` `thenApply()` `await()` — CompletableFuture 链式操作、线程池、锁、信号量、原子变量 |
| **完整反射** | `getField/setField` `invokeMethod/invokeStatic` `newInstance` — 字段/方法/构造器/枚举/数组的全方位反射 |
| **集合桥接** | `newArrayList/HashMap/HashSet` `asList/asMap` `toTable` — Lua 表与 Java 集合双向转换 |
| **Stream API** | `streamMap/Filter/ForEach/Collect` `collectorsToList/ToMap/GroupingBy` — 完整 Java Stream 支持 |
| **Optional** | `optionalOf/Get/OrElse` — Java Optional 支持 |
| **类元信息** | `classOf` `superClass` `interfaces` `isInstance` `cast` — 运行时类型查询 |

---

## 快速开始

### 安装
1. 将 `MinecraftX.jar` 放入服务器的 `plugins/` 目录
2. 启动服务器，插件会在服务器根目录自动创建 `MinecraftX/` 文件夹
3. 目录结构：
   ```
   MinecraftX/
   ├── script/        # 独立 Lua 脚本
   ├── module/        # 模块
   ├── config/        # 模块配置文件
   └── packages/      # 模块安装包 (.zip)
   ```

### 运行脚本
```lua
-- MinecraftX/script/hello.lua
broadcast("&aHello, MinecraftX!")
log("INFO", "Script loaded!")
```
```
/script run hello.lua
```

### 创建模块
`MinecraftX/module/example/manifest.yml`:
```yaml
name: "example"
version: "1.0"
main: "main.lua"
author: "example"
```

`MinecraftX/module/example/scripts/main.lua`:
```lua
function onEnable()
    broadcast("&aModule " .. SCRIPT_NAME .. " enabled!")
end

function onDisable()
    broadcast("&cModule " .. SCRIPT_NAME .. " disabled!")
end
```

---

## Lua API 参考

### Bukkit/Paper 全局函数

| 函数 | 说明 |
|---|---|
| `registerEvent(className, [priority], callback)` | 注册事件监听器 |
| `broadcast(message)` | 全服广播（支持 `&` 颜色代码） |
| `log(level, message)` | 日志输出 |
| `runTask(callback)` | 下一 tick 执行 |
| `runTaskLater(delay, callback)` | 延迟执行 |
| `runTaskTimer(delay, period, callback)` | 定时执行 |
| `component(text)` | 创建 Adventure 文本组件 |
| `sendMessage(player, text)` | 发送消息 |
| `sendTitle(player, title, subtitle, [fadeIn], [stay], [fadeOut])` | 发送标题 |
| `playSound(player, location, name, volume, pitch)` | 播放音效 |
| `initConfig(scriptName, [defaults])` | 初始化脚本配置 |

### 全局变量
- `plugin` — Bukkit JavaPlugin 实例
- `server` — Bukkit Server 实例
- `configManager` — 配置管理器
- `SCRIPT_NAME` — 当前脚本/模块名称
- `luajava` — LuaJ luajava 库

### 模块系统函数

| 函数 | 说明 |
|---|---|
| `export(name, value)` | 导出函数（需 `expose: true`） |
| `import(packageName)` | 导入其他模块的导出函数 |
| `loadModuleConfig()` | 加载模块配置 |
| `saveModuleConfig(config)` | 保存模块配置 |

---

## 命令

| 命令 | 说明 |
|---|---|
| `/script run <文件名>` | 执行 Lua 脚本 |
| `/script list` | 列出所有脚本 |
| `/mpm install <zip>` | 安装模块包 |
| `/mpm remove <模块名>` | 移除模块 |
| `/mpm pack <模块名>` | 打包为 ZIP |
| `/mpm pack <模块名...>` | 多模块打包 |
| `/mpm list [filter status/author]` | 列出模块 |
| `/mpm list info <模块名>` | 查看模块信息 |
| `/mpm disabled <模块名>` | 禁用模块 |
| `/mpm enabled <模块名>` | 启用模块 |

---

## 构建

```bash
./gradlew build
```

产物位于 `build/libs/MinecraftX-1.0-SNAPSHOT-all.jar`。

### 依赖
- Paper API 1.21.8-R0.1-SNAPSHOT
- Kotlin JDK 8 标准库 2.4.0
- LuaJ 3.0.2
