# 揭棋联机对弈项目

## 项目简介

这是一个基于 `Java + Maven + JavaFX + Socket` 的揭棋联机对弈程序。
服务器负责配对和转发走子信息，客户端在连接成功后自动弹出图形界面。

## 当前功能

- 服务器配对两个客户端
- 客户端自动弹出图形化界面
- 象棋棋盘线框与楚河汉界显示
- 棋子位于交点上
- 支持鼠标点击操作
- 支持基本走子和翻子同步

## 项目结构

- `org.example.server.ServerMain`：服务器启动入口
- `org.example.client.ClientMain`：客户端启动入口
- `org.example.net.GameServer`：联机服务器
- `org.example.ui.GameApp`：JavaFX 界面
- `org.example.common.*`：棋盘、棋子、走子、规则基础类

## 运行方式

### 1. 启动服务器

```powershell
cd D:\java\JAVA-ObjectOriented
mvn -q exec:java -Dexec.mainClass=org.example.server.ServerMain
```

### 2. 启动客户端

在两台电脑上分别启动：

```powershell
cd D:\java\JAVA-ObjectOriented
mvn -q exec:java -Dexec.mainClass=org.example.client.ClientMain -Dexec.args="服务器IP 5000"
```

例如本机测试可用：

```powershell
mvn -q exec:java -Dexec.mainClass=org.example.client.ClientMain -Dexec.args="127.0.0.1 5000"
```

## IDEA 配置

建议创建 3 个运行配置：

- `ServerMain`
- `ClientMain-1`
- `ClientMain-2`

启动顺序：

1. 先运行服务器
2. 再运行第一个客户端
3. 再运行第二个客户端

## 说明

当前版本是可运行的联机骨架，适合继续扩展完整揭棋规则、胜负判定和 AI 博弈模块。

## DeepSeek AI 客户端

AI 客户端可以作为一个无 GUI 的 Socket 客户端加入现有服务器。它会先用本地规则引擎生成合法候选走法，再调用 DeepSeek API 选择候选；如果没有配置 API key 或 API 返回不可用结果，会使用本地启发式兜底。

### 环境变量

```powershell
$env:DEEPSEEK_API_KEY="你的 DeepSeek API Key"
$env:DEEPSEEK_MODEL="deepseek-v4-flash"
$env:DEEPSEEK_TIMEOUT_MILLIS="8000"
$env:AI_MAX_CANDIDATES="40"
```

不设置 `DEEPSEEK_API_KEY` 时，AI 会自动进入本地启发式模式。

### 本地配置文件

也可以在项目根目录放一个 `ai.local.properties` 文件，AI 客户端启动时会自动读取它。仓库里提供了示例文件 [ai.local.properties.example](</C:/Users/stay g/Documents/New project 2/AI-Jieqi-master/ai.local.properties.example>)。

```properties
deepseek.api.key=你的 DeepSeek API Key
deepseek.api.model=deepseek-v4-flash
deepseek.api.timeoutMillis=20000
deepseek.ai.maxCandidates=40
```

`ai.local.properties` 已加入 `.gitignore`，不会默认提交。

### 启动方式

先启动服务器：

```powershell
mvn -q exec:java -Dexec.mainClass=org.example.server.ServerMain
```

再启动真人客户端或另一个 AI 客户端。启动 AI 客户端：

```powershell
mvn -q exec:java -Dexec.mainClass=org.example.ai.AiClientMain -Dexec.args="127.0.0.1 5000"
```

AI 每步会输出一行决策日志，包含候选 ID、走法、来源、耗时和兜底原因。
