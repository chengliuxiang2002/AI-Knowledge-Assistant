# AI Knowledge Assistant 前端

基于 **Vue 3 + Vite** 构建的轻量级 AI 知识助手聊天组件，专为个人博客/知识站点场景设计，支持一键嵌入任意网页。

## 功能特点

- 💬 **多轮对话**：基于 SSE 流式输出，实时展示 AI 回复
- 📚 **RAG 知识库问答**：一键开关，启用基于自有知识库的智能问答
- 📱 **响应式设计**：桌面端居中卡片，移动端全屏适配
- 🚀 **轻量零依赖**：仅依赖 Vue + Axios，单文件组件即可部署

## 技术栈

- Vue 3 (`<script setup>`)
- Vite 4
- EventSource (SSE 流式消费)

## 开发说明

### 环境要求

- Node.js >= 18
- npm >= 9

### 安装依赖

```bash
npm install
```

### 启动开发服务器

```bash
npm run dev
```

开发服务器运行在 `http://localhost:3000`，已配置 `/api` 代理到 `http://localhost:8123`。

### 构建项目

```bash
npm run build
```

构建产物输出到 `dist/` 目录，可直接部署到任意静态服务器或博客平台。

## 后端接口

前端依赖以下后端接口：

- `GET /api/assistant/chat/stream` — 基础流式对话
- `GET /api/assistant/chat/rag/stream` — RAG 知识库流式问答

## Docker 部署

```bash
docker build -t ai-assistant-frontend .
docker run -p 80:80 ai-assistant-frontend
```

Nginx 已配置将 `/api` 反向代理到后端服务（容器名 `backend`，端口 8123）。
