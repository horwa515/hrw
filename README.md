# PPT Format Converter V1.1

适配 Windows 10 x64 / 公司现有 Java 8（含 1.8.0_201）的本地 PPTX 转换工具。双击启动后在浏览器上传、批量转换和下载，运行时不需要互联网、Python、JDK 或 PowerPoint。

## 新版下载：两个 ZIP 的解压密码都是 3609

| 版本 | 下载 | PPT 打开密码处理 |
| --- | --- | --- |
| 基础版，约 80 KB | [下载 Basic ZIP](https://github.com/horwa515/hrw/raw/refs/heads/main/downloads/PPTFormatConverter-1.1-Java8-Basic-Password.zip) | 输入先手动去密，输出如需密码再手动设置 |
| 增强版，约 6.65 MB | [下载 Enhanced ZIP](https://github.com/horwa515/hrw/raw/refs/heads/main/downloads/PPTFormatConverter-1.1-Java8-Enhanced-Password.zip) | 自动用 3609 打开加密输入，所有输出设置打开密码 3609 |

两版都不附带 Java，直接使用公司已有 Java 8。增强版包含 Office 加密库，所以体积大一些；两版均小于 12 MB。旧版 V1.0 文件仍保留在 downloads 文件夹供回退，不包含本次新增规则。

如果直接下载没有反应，登录 GitHub 后打开 [downloads 文件夹](https://github.com/horwa515/hrw/tree/main/downloads)，点击对应 ZIP，再点击右上角 **Download raw file**。

## 使用步骤

1. 将需要的 ZIP 作为邮件附件发送，完整解压，输入密码 **3609**。
2. 打开解压后的 `PPTFormatConverter-1.1-Basic` 或 `PPTFormatConverter-1.1-Enhanced` 文件夹，双击 **Start.cmd**。
3. 保持程序窗口打开，在自动出现的网页选择或拖入 `.pptx` 文件，点击“开始转换”。如果网页没有打开，把程序窗口中的本机地址复制到 Edge / Chrome / Firefox。
4. 查看每个文件的进度、错误和兼容性提示，下载单个 PPTX 或全部成功结果的 ZIP。单个失败不影响其他文件。
5. 增强版的输出 PPTX 在 PowerPoint 中打开时输入 **3609**。原本无密码的输入也会得到有密码的输出。
6. 下载完成后再关闭程序窗口。只关闭浏览器标签页不会停止服务；正常退出会清理临时文件。

**ZIP 解压密码和 PPT 打开密码是两层独立的密码，此处均按要求设置为 3609。** 基础版只使用 ZIP 解压密码；增强版同时自动处理 PPT 打开密码。

示例 `sample-company-1.4444.pptx` 可验证公司常用页面比例。增强版另带 `sample-company-3609.pptx`，打开密码为 3609，可直接上传测试自动去密与重新加密。

## 转换规则

- 读取实际页面宽高，不依赖 PowerPoint 的预设名称。**4:3（1.3333）和公司常见 1.4444（13:9）** 都扩展为 16:9（1.7778），每一类各允许 0.5% 相对误差。1:1、1.4、已有 16:9 等其他比例保持尺寸，并提示。
- 高度不变；新宽度为 `round(高度 × 16 / 9)`，水平平移量为 `round((新宽度 − 原宽度) / 2)`，只改变对象 x，保持 y、宽高、旋转和翻转。舍入误差最多 0.5 EMU。
- 幻灯片、版式、母版同时处理，保持占位符继承；组合形状只移动外层，内部子对象不重复移动。
- **可编辑中文使用黑体（SimHei）**，英文只将 `微软雅黑` / `Microsoft YaHei` 改为 Arial，其他英文字体不改。
- 中英文混排的普通 DrawingML 文本按文字分别设置字体，同时保留文本顺序、字号、颜色、粗斜体和超链接；动态字段保留字段 ID，不拆字段。主题、备注、图表及默认样式的东亚字体同时设为 SimHei。
- 输出另存为 `_converted.pptx`，不覆盖原文件。同名文件独立处理，在整批 ZIP 中为重复输出名追加编号。

## 密码支持与对象保留

增强版只处理标准 Office PPTX 的“打开密码”：已知密码 3609 时解密输入，完成转换后用 Office Agile AES-128 / SHA-512 加密输出。支持 Agile 及常见的 Standard Office 加密输入。密码错误的文件单独失败，不尝试猜测其他密码。旧 `.ppt`、企业 IRM、特殊权限保护不在支持范围。

转换采用 ZIP + XML 最小修改，不把页面渲染成图片。图像、嵌入工作簿、OLE 数据、关系文件等二进制部件保留原始内容。被修改的 XML 重新序列化，保留节点、命名空间和扩展数据，XML 排版形式可能变化。加密增强版仅在外层增添标准 Office 保护，解密后仍是可编辑 PPTX。

- **字体**：目标电脑需要已安装黑体（SimHei），字体变化可能改变字宽和换行，请复查。图片里烧录的中文不执行 OCR；嵌入 Excel / OLE 内部文字不改写。非 DrawingML 的专有对象文字可能由其自身软件决定。
- **背景**：背景仍按新页面边界铺设，背景图片或渐变显示范围可能改变；普通图片对象不缩放。
- **动画和高级对象**：原始 XML 和对象数据尽量保留，但按页面比例定义的运动路径可能变化；OLE、SmartArt、扩展内容和旧式 VML 会记录提示，建议在 PowerPoint 中人工复查。
- **数字签名、Strict OOXML、损坏文件**：明确拒绝或报告失败，不做破坏性降级。请先另存为标准无签名 `.pptx`。
- **外部链接**：不下载外部内容，离线电脑上的外部媒体或数据源可能不可用。

## 构建与测试

基础版无第三方 Java 依赖，从源码构建需要 JDK 8+；运行只需 Java 8 JRE。JDK 9+ 使用 `--release 8`，JDK 8 使用 `-source 8 -target 8`，所有应用类的 class major version 为 52。

Linux / macOS：

```sh
export JAVA_HOME=/path/to/jdk8
bash build.sh
# 增强版依赖首次下载需要联网；下载后可离线编译、运行
python3 scripts/fetch_office_dependencies.py
bash build-enhanced.sh
```

Windows 开发机：

```bat
set "JAVA_HOME=C:\path\to\jdk8"
build.cmd
python scripts\fetch_office_dependencies.py
build-enhanced.cmd
```

产物为 `dist/ppt-format-converter.jar` 和 `dist/ppt-office-encryption.jar`。增强版运行还需 `build/office-libs` 中的 JAR，启动命令示例：

```sh
# 基础版
java -jar dist/ppt-format-converter.jar --port 18123 --no-browser
# 增强版（Windows 将类路径分隔符 : 改为 ;）
java -cp 'dist/ppt-format-converter.jar:dist/ppt-office-encryption.jar:build/office-libs/*' com.pptconverter.App --office-password --port 18124 --no-browser
```

增强版采用 Apache POI 5.4.1 及其声明的 Java 8 兼容依赖。下载脚本校验 Maven Central 的官方校验值及仓库锁定的 SHA-512；版本、来源和摘要见 `scripts/office-dependencies.lock.json`。原始许可证和第三方 notices 保留在各依赖 JAR 的 META-INF 中，打包附带依赖清单。运行无需联网。

`build.sh` / `build.cmd` 执行 **16 项基础测试**；增强版构建再执行 **7 项测试**，涵盖 Office 加密往返、错误密码、无密码输入的输出保护、旧式 Standard 加密、原文件及临时明文保护和真实 HTTP 混合批次。

另用独立 `msoffcrypto-tool` 验证输出密码、Agile 数据完整性，并用其生成加密输入交叉验证。Chromium / Playwright 验证网页上传、失败隔离、单个和批量下载、日志、新批次及手机布局。测试结论见 [VALIDATION.md](docs/VALIDATION.md)。本机为 Linux Java 8u202，尚未在 Windows PowerPoint 中实际验证新版排版及密码弹窗。

## 打包

```sh
# 先构建两版；增强版的加密示例已包含在 examples 中
python3 scripts/package_versions.py
```

打包脚本需要构建机已安装 Info-ZIP `zip`，逐一检查应用类为 Java 8 字节码、依赖摘要、ZIP 密码及解压后的文件内容。生成两个带密码的 Windows ZIP；无需 Windows 安装器。已有同名输出会拒绝覆盖，请先保留或移走旧包。构建机的 Python、JDK 和打包工具不进入交付包。

## 日志、资源与临时文件

只监听 `127.0.0.1`，静态页面及 API 均在本机，API 使用每次启动的令牌并检查 Host / Origin。单文件上限 128 MB，每批最多 20 个文件、累计 256 MB；解压总量上限 512 MB、单 XML 16 MB、包内最多 20,000 部件。两个工作线程独立处理文件。

临时输入和转换输出位于系统临时目录中的独立应用目录。增强版的临时明文在转换或失败后清理；输入副本在转换后删除，结果保留至删除批次、正常退出或 24 小时无访问后的定时清理。异常终止可能遗留临时目录，可确认程序停止后清理。日志记录尺寸、字体调整、警告、失败原因和输出密码状态，可单独下载，批次 ZIP 也附带日志。

## 源码结构

`src/main/java` 为基础引擎、本机服务及中英文字体处理；`src/enhanced` 为可选 Office 加密适配器和测试；`src/main/resources/web` 为中文页面；`src/test` 为基础测试；`scripts` 包含依赖获取和打包；`examples` 含比例样本；`docs` 含使用说明和验证记录。
