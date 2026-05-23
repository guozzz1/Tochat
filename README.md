# ToChat

AI 对话 + 生图 Android 应用，基于 OpenAI 兼容 API，支持聊天对话和图片生成。

## 功能特性

### AI 对话
- 流式输出，实时显示回复
- Markdown 渲染支持
- 自动降级到非流式请求

### 图片生成
- 支持自定义 API 端点和 Key
- 多比例选择（智能、1:1、3:4、4:3、9:16、16:9）
- 模板库快速生图（含中文提示词）
- 关键词识别自动切换聊天/生图模式

### 设置管理
- 独立配置对话和生图服务（URL / Key / 模型）
- 一键刷新模型列表
- 自定义聊天背景（含裁剪功能）
- 存储清理

## 使用方式

1. 进入设置，填写对话配置（Base URL + API Key），点击刷新模型并选择
2. 可选：填写生图配置，用于独立的生图模型
3. 返回聊天页，直接输入文字即可对话
4. 输入含"生成"、"画"等关键词会自动调用生图模型

## 技术栈

- Kotlin + Jetpack Compose
- Hilt 依赖注入
- Room 本地存储
- Retrofit + OkHttp 网络请求
- Coil 图片加载
- Markwon Markdown 渲染
- Android Image Cropper 图片裁剪

## 构建

```bash
./gradlew assembleDebug
```

需要 Android SDK 34，最低支持 Android 8.0 (API 26)。
