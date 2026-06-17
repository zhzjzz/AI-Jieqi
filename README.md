# 揭棋联机对战项目

## 项目简介

这是一个基于 `Java + Maven + JavaFX + WebSocket + JSON` 的揭棋联机对战程序。服务端负责匹配、开局、校验走子、广播走子结果和结束消息；客户端通过公共 JSON 接口与服务端通信。

## 当前功能

- 服务端支持 WebSocket JSON 消息通信
- 支持公共接口文档中的登录、注册、匹配、取消匹配、请求先手、准备、心跳、认输、走子消息
- 支持匹配成功、游戏开始、走子结果、错误、房间状态、超时结构、游戏结束等服务端消息
- 图形客户端自动连接并开始匹配
- AI 客户端自动匹配并通过同一 JSON 协议对战
- 复用现有揭棋棋盘、翻子、走子、吃子、将军/胜负判断逻辑

## 运行方式

### 1. 启动服务端

```powershell
mvn -q exec:java -Dexec.mainClass=org.example.server.ServerMain -Dexec.args="5000"
```

服务端地址为：

```text
ws://localhost:5000
```

### 2. 启动图形客户端

分别启动两个客户端：

```powershell
mvn -q exec:java -Dexec.mainClass=org.example.client.ClientMain -Dexec.args="127.0.0.1 5000"
```

### 3. 启动 AI 客户端

```powershell
mvn -q exec:java -Dexec.mainClass=org.example.ai.AiClientMain -Dexec.args="127.0.0.1 5000"
```

### 4. 启动 AI 对 AI 图形演示

这个模式不需要先启动服务端，会直接弹出棋盘窗口并自动播放红黑双方 AI 对弈全过程。

```powershell
mvn '-Dmaven.repo.local=.m2\repository' -q javafx:run
```

## 协议说明

客户端和服务端通过 JSON 字符串交换消息，每条消息都包含 `messageType` 字段。主要消息包括：

- 客户端发送：`Login`、`register`、`startMatch`、`cancelMatch`、`requestFirstHand`、`Ready`、`move`、`ping`、`Resign`
- 服务端发送：`loginResult`、`matchSuccess`、`gameStart`、`moveResult`、`timeout`、`gameOver`、`pong`、`error`、`roomInfo`

棋盘由 `gameStart.initialBoard` 下发，走子使用文档要求的 `fromX/fromY/toX/toY/isFlip` 字段。
