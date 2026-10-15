# 本次交付的验证结果

- Linux x64，Oracle JDK 21 编译，`--release 17` 生成 Java 17 字节码。
- `bash build.sh` / 云环境安装脚本：14 项单元与 HTTP 集成测试全部通过，0 失败、0 跳过；安装脚本在无专用 JDK 目录时完成下载、SHA-256 验证、安装和构建。
- Chromium / Playwright：真实网页混合上传 4:3、损坏、16:9 三个文件；2 成功、1 失败；单文件、ZIP、日志下载及新批次通过；390px 手机布局没有水平溢出，没有 JavaScript 页面错误。
- 独立 `python-pptx` 打开网页下载的输出：2 页，宽 12192000 EMU、高 6858000 EMU；1 个可编辑表格和 1 个可编辑图表。
- Windows x64 Temurin JRE 21.0.12.1+1：从 Adoptium 官方 GitHub 发布下载，SHA-256 与官方校验文件一致。
- Windows 离线 ZIP：全部 ZIP 成员通过 CRC 验证；包含 `runtime/bin/java.exe`、运行时许可证、应用 JAR、CRLF 启动脚本、示例及说明；JAR 与包内清单的 SHA-256 一致。

本次没有 Windows 主机或 PowerPoint，**Windows 双击启动、字体回退实际排版和动画播放尚未人工验收**。没有生成 Windows jpackage 原生 EXE；已提供 Windows 构建脚本。浏览器测试属于可选开发验证，Playwright 不进入离线程序。

## 小型离线包补充验证

- 移除 `java.desktop` 依赖，使用系统命令打开浏览器；更新后 14 项测试仍全部通过。`jdeps` 确认仅依赖 `java.base,java.xml,jdk.httpserver`。
- 相同三个模块的 Linux 最小运行时启动应用，真实 HTTP 上传、转换和下载通过；最小运行时的 Chromium 混合批次、下载、日志及手机布局通过。
- 使用官方校验通过的 Windows JDK jmods 交叉链接小型 Windows 运行时。ZIP 完整性检查、x64 PE 启动文件检查、模块清单及许可证检查通过。
- 小型离线 ZIP 约 17 MB，MIME 邮件编码约 23.3 MB；不带 Java 的版本约 40 KB。Windows 原生运行仍需实际 Windows 主机验收。
