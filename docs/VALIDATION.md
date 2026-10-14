# 本次交付的验证结果

- Linux x64，Oracle JDK 21 编译，`--release 17` 生成 Java 17 字节码。
- `bash build.sh` / 云环境安装脚本：14 项单元与 HTTP 集成测试全部通过，0 失败、0 跳过；安装脚本在无专用 JDK 目录时完成下载、SHA-256 验证、安装和构建。
- Chromium / Playwright：真实网页混合上传 4:3、损坏、16:9 三个文件；2 成功、1 失败；单文件、ZIP、日志下载及新批次通过；390px 手机布局没有水平溢出，没有 JavaScript 页面错误。
- 独立 `python-pptx` 打开网页下载的输出：2 页，宽 12192000 EMU、高 6858000 EMU；1 个可编辑表格和 1 个可编辑图表。
- Windows x64 Temurin JRE 21.0.12.1+1：从 Adoptium 官方 GitHub 发布下载，SHA-256 与官方校验文件一致。
- Windows 离线 ZIP：全部 ZIP 成员通过 CRC 验证；包含 `runtime/bin/java.exe`、运行时许可证、应用 JAR、CRLF 启动脚本、示例及说明；JAR 与包内清单的 SHA-256 一致。

本次没有 Windows 主机或 PowerPoint，**Windows 双击启动、字体回退实际排版和动画播放尚未人工验收**。没有生成 Windows jpackage 原生 EXE；已提供 Windows 构建脚本。浏览器测试属于可选开发验证，Playwright 不进入离线程序。
