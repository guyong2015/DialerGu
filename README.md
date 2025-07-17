<p align="center">
  <img src="dg.png" alt="应用Logo" width="128"/>
</p>

<h1 align="center">DialerGu</h1>

<p align="center">
  AI generate list & dial for android Mobile camera.
</p>
<p align="center">
  智能生成拨号清单 & 拨打
</p>


<p align="center">
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT"></a>
    <a href="#"><img src="https://img.shields.io/badge/Android-11.0%2B-green.svg" alt="Platform: Android"></a>
    <a href="https://github.com/guyong2015/DialerGu/releases"><img src="https://img.shields.io/github/v/release/guyong2015/DialerGu" alt="Release Version"></a>
    <a href="https://github.com/guyong2015/DialerGu/releases"><img src="https://img.shields.io/github/downloads/guyong2015/DialerGu/total" alt="GitHub Downloads"></a>
</p>

## 📺 演示和说明
*  **[点击这里访问演示视频或详细说明](https://guyong2015.github.io/DialerGu/)**

## 🚀 快速开始
### 安装

您可以通过以下方式安装本应用：

*   前往 **[GitHub Releases](https://github.com/guyong2015/DialerGu/releases)** 页面下载最新的 `.apk` 文件。
*   (如果已上架) 前往 **[Google Play](https://play.google.com/store/apps/details?id=your.package.name)** 下载。

### 从源码构建

如果您想自行编译，请遵循以下步骤：
*   (当前使用阿里千问OCR模型,测试通过的还的Gemini.不同模型需要修改代码适应返回数据)
1.  **克隆仓库**
    ```bash
    git clone https://github.com/guyong2015/DialerGu.git
    ```
2.  **打开项目**
    使用 Android Studio 打开项目。
3.  **配置 (需要)**
    如果项目需要API密钥等，请在 `local.properties` 文件中添加如下内容 (此文件已被加入 `.gitignore`，不会被提交)：
    ```properties
    API_KEY="YOUR_API_KEY"
    ```
4.  **构建并运行**
    点击 "Run 'app'"。
## 🤝 如何贡献

    非常欢迎您的加入！我们欢迎任何形式的贡献，无论是报告一个bug、提交一个功能请求，还是直接贡献代码。
    
    *   **报告问题**: 请通过 **[GitHub Issues](https://github.com/guyong2015/DialerGu/issues)** 提交。
    *   **贡献代码**: 请 Fork 本仓库，创建您的特性分支，完成修改后提交 Pull Request。
    
    我们鼓励您在开始工作前，先阅读我们的 **[贡献指南 (CONTRIBUTING.md)](CONTRIBUTING.md)**。
## 🙏 致谢

这个项目的完成离不开以下优秀的开源库和工具：

*   **[Retrofit](https://square.github.io/retrofit/)**: 用于网络请求。
*   **[Glide](https://github.com/bumptech/glide)**: 用于图片加载。
*   **[Lottie](https://airbnb.design/lottie/)**: 用于实现炫酷的动画。
*   感谢 **@某个贡献者** 提出的宝贵建议。
*   设计灵感来源于 **[某个项目或网站](http://example.com)**。
## 📄 许可证

本项目采用 MIT 许可证。详情请见 [LICENSE](LICENSE) 文件。
