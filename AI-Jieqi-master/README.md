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
