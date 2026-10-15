# PPT Format Converter V1.0

Java 本地 PPTX 转换工具。双击启动后打开中文网页，选择或拖入文件，批量将 **4:3 扩展为 16:9**，下载可编辑 PPTX。应用在本机运行，使用期间不需要互联网，也不需要安装 PowerPoint。

## 直接下载（普通用户选第一个）

- **[公司电脑已有 Java 8：下载 Java 8 专用包（约 70 KB）](https://github.com/horwa515/hrw/raw/refs/heads/main/downloads/PPTFormatConverter-1.0-Windows-Java8.zip)**：适配 Windows 10 x64、现有 Java 8（含 1.8.0_201）；无需安装或升级 Java。包含启动脚本、说明和示例 PPT，邮件附件远小于 12 MB。
- [电脑没有 Java：小型 Windows 离线 ZIP（约 17 MB）](https://github.com/horwa515/hrw/raw/refs/heads/main/downloads/PPTFormatConverter-1.0-Windows-x64-Small.zip)：自带最小 Java 运行时，完整解压后双击 `Start.cmd`。邮件编码后约 23.3 MB，通常适合 25 MB 邮件限制。
- [仅程序精简 ZIP（约 40 KB）](https://github.com/horwa515/hrw/raw/refs/heads/main/downloads/PPTFormatConverter-1.0-Java-required.zip)：工作电脑**必须已安装 Java 8+**，不附带示例。
- [原始完整运行时 ZIP（约 49 MB）](https://github.com/horwa515/hrw/raw/refs/heads/main/downloads/PPTFormatConverter-1.0-Windows-x64.zip)：旧版便携包，仍可运行，但不推荐用于邮件附件。
- [完整源码 ZIP](https://github.com/horwa515/hrw/raw/refs/heads/main/downloads/PPTFormatConverter-1.0-Source.zip)：给开发者使用。

若直接下载链接没有开始下载，请先登录 GitHub，再打开 [downloads 文件夹](https://github.com/horwa515/hrw/tree/main/downloads)，点击对应 ZIP，使用文件页右上角的下载按钮（Download raw file）下载。不要点击仓库的 “Code → Download ZIP” 来代替自带运行时的离线程序包。

## Windows 用户：解压、双击、转换

**已有 Java 8（例如公司提供的 1.8.0_201）**：下载上面的 Java 8 专用包，完整解压后打开 `PPTFormatConverter-Java8` 文件夹，双击 `Start.cmd`。不需要安装 JDK、不需要配置 JAVA_HOME。启动脚本优先使用系统 PATH 中的 Java。详细步骤见 [Java 8 使用说明](docs/Java8使用说明.txt)。建议用 Edge / Chrome / Firefox，应用不支持旧 Internet Explorer。

**电脑没有 Java**，使用自带运行时的小型离线包：

1. 下载推荐的 `PPTFormatConverter-1.0-Windows-x64-Small.zip`，**完整解压**到任意可写目录。不要直接在 ZIP 内启动。
2. 打开解压后的 `PPTFormatConverter` 文件夹，双击 **`Start.cmd`**。离线包自带 Eclipse Temurin Java 21 运行时，无需配置 Java。
3. 浏览器会自动打开转换页面。如果没有打开，将程序窗口中的地址复制到 Edge / Chrome / Firefox。
4. 点击“选择文件”或拖入一个或多个 `.pptx`，点击“开始转换”。
5. 查看每个文件的进度、尺寸和兼容性提示。下载单个 PPTX，或下载全部成功结果的 ZIP；也可下载转换日志。
6. 下载后在 PowerPoint 中打开检查。**退出前先保存结果**：关闭程序窗口或按 `Ctrl+C` 会停止服务并清理临时文件。关闭浏览器标签页不会停止程序。

支持 Windows 10 / 11 **x64**。Windows ARM64 可否通过系统模拟运行尚未验证，不提供原生 ARM 包。旧 `.ppt` 请先在 PowerPoint 中另存为 `.pptx`。

离线包是“解压即用”的便携应用，入口是 `Start.cmd`。如需要独立 EXE 入口，Windows 开发者可运行下文的 `package-windows.cmd`，得到带运行时的应用目录。

## 转换规则

- 从 `ppt/presentation.xml` 读取页面宽高，采用 EMU 坐标单位；`914400 EMU = 1 inch`。
- 4:3 检测允许 0.5% 相对误差，以容纳尺寸舍入；`新宽度 = round(原高度 × 16 / 9)`，高度不变。
- 水平平移量 `Δx = round((新宽度 − 原宽度) / 2)`。只改变顶层对象的 `x`，保持 `y`、宽高、旋转和翻转参数。舍入误差最多 0.5 EMU。
- 幻灯片、版式、母版同时处理。占位符保留继承关系；组合形状只移动外层坐标，不重复移动内部子对象。
- `微软雅黑` 和不区分英文大小写的 `Microsoft YaHei` 字体使用声明替换为 `Arial`；微软雅黑 Light、宋体、Times New Roman 等其他字体不修改。
- 非 4:3（包括已经是 16:9）的输入仍可输出：**保留尺寸和几何坐标，只替换指定字体并记录提示**。输出名称使用 `_converted.pptx`，不会混淆为已扩展页面。
- 文件始终另存为新 PPTX。原文件不覆盖、不写入；同名输入各自处理，在批次 ZIP 中自动为输出追加编号。

### 保留与兼容性边界

采用 ZIP + XML 的最小修改方式，不把页面渲染成图片。图片、图表嵌入工作簿、OLE 二进制、关系文件等非 XML 内容逐字节保留；未修改的 XML 部件也逐字节保留。被修改的 XML 会重新序列化，保留节点、命名空间、属性、文本和扩展结构，XML 排版形式可能变化。

图片、表格、文本框、图表、连接线、组合形状和显式坐标的母版对象保持原尺寸。高级对象的原始数据被保留，**这不等于所有 PowerPoint 版本的高级功能都已验证**。

- **中文字体**：Arial 不覆盖全部中文字形，Windows / PowerPoint 会回退到系统字体。中文字宽、换行或文本框溢出可能变化；本工具不安装字体，也不执行渲染测量。请在实际使用电脑检查，必要时在 PowerPoint 中调整文本布局。
- **背景填充**：页面背景仍依照新页面边界铺设，渐变或背景图片的显示范围可能变化；普通图片对象不被拉伸。检测到背景时记录警告。
- **动画**：XML 不重建或删除。按页面比例定义的运动路径可能随新宽度改变轨迹，检测到 `animMotion` 时记录警告。尚未在 Windows PowerPoint 内人工验证动画播放。
- **OLE、扩展内容、未知对象**：保留并记录相应警告；对象编辑可能需要对应软件。没有位置坐标且无法继承的对象会保留原样，并提示无法保证居中。
- **嵌入字体 / VML / 外部链接**：嵌入字体资源及名称绑定保留，文本使用声明仍替换为 Arial；旧式 VML 坐标和样式字体原样保留并提示。外部链接不下载，链接媒体或数据源在离线电脑上可能不可用。
- **Strict OOXML、加密、损坏、数字签名文件**：不进行破坏性降级。转换失败并给出提示；请先另存为无加密、无签名的标准 `.pptx`。
- 一份 PPTX 的幻灯片共享一个页面尺寸，工具不提供按单页选择不同比例。

## 构建与运行（开发者）

运行新版 JAR 需要 Java **8+**，已有 Java 8 JRE 即可；从源码构建需要 **JDK 8+**。没有 Maven、Gradle 或第三方 Java 依赖，因此安装好 JDK 后，构建和测试均可离线运行。JDK 9+ 编译时使用 `--release 8`，JDK 8 编译时使用 `-source 8 -target 8`。

Windows：

```bat
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21"
build.cmd
Start.cmd
```

Linux / macOS：

```sh
export JAVA_HOME=/path/to/jdk
bash build.sh
bash start.sh
```

构建脚本编译 Java 8 字节码（class major version 52）、打包网页资源、运行测试，生成 `dist/ppt-format-converter.jar`。直接运行：

```sh
java -jar dist/ppt-format-converter.jar
# 自动选择可用端口；默认尝试打开浏览器
java -jar dist/ppt-format-converter.jar --port 18123 --no-browser
```

只监听 IPv4 回环地址；端口为自动分配或指定值。云环境没有桌面浏览器时使用 `--no-browser`，通过本机 HTTP 请求验证服务。云环境入门界面不提供本地网页预览。

云环境已保存 JDK 安装 / 构建脚本和服务启动指引；安装脚本也位于 `scripts/cloud-install.sh`。服务启动后可用 `python scripts/http-smoke.py --url http://127.0.0.1:18123` 验证真实上传、转换与下载，无第三方 Python 依赖。

### Windows EXE 应用目录

在 Windows 上安装 JDK 17+（推荐当前受支持的 Temurin 21），双击 / 执行：

```bat
package-windows.cmd
```

输出 `dist\windows\PPTFormatConverter\PPTFormatConverter.exe`。复制**完整应用目录**即可离线部署，不能只复制 EXE。`jpackage --type app-image` 不需要 WiX；程序保留控制台窗口，方便查看启动地址和停止服务。这一步依赖 Windows JDK，Linux 不能生成或验证 Windows 原生 EXE。

### Java 8 公司电脑包（最小交付）

```sh
python scripts/package_java8.py
```

先运行构建脚本。打包脚本检查 JAR 中所有类都是 Java 8 字节码（52），再打包启动脚本、UTF-8 使用说明和一个示例 PPT；不附带 Java 运行时。现有 Oracle Java 8u201 的 Windows 公司电脑可直接使用，无需改变系统 Java。实际测试环境为 Linux AdoptOpenJDK 8u202；尚未在 Windows Oracle 8u201 上执行，需用户验收启动及 PowerPoint 排版。

### 在任意构建机生成自带运行时的 Windows 便携 ZIP

推荐的小型离线包使用官方 Windows JDK ZIP 和匹配的官方 SHA-256 文件，经同主版本的 JDK `jlink` 生成运行时。仅保留 `java.base,java.xml,jdk.httpserver`；系统浏览器由操作系统命令启动，应用不依赖 `java.desktop`。采用共享字符串处理后再 ZIP 压缩，省去用于 JNI 开发的 `.lib` 链接库，保留运行必需的 DLL 和许可证。Windows 包可在 Linux 上通过 Windows jmods 交叉链接，但仍需 Windows 主机验收启动。

```sh
python scripts/package_small.py --windows-jdk /path/to/official-windows-jdk.zip --checksum-file /path/to/official-windows-jdk.zip.sha256.txt --jlink /path/to/jdk21/bin/jlink
# 不带运行时的约 40 KB 版本（使用者必须有 Java 8+）
python scripts/package_small.py --java-required
```

`jdeps --print-module-deps dist/ppt-format-converter.jar` 应输出上述三个模块；应用引入新模块后，需要同步更新打包模块清单。打包脚本拒绝覆盖已有输出。

先构建 JAR，然后从 [Temurin 官方 Windows x64 JRE 发布页](https://github.com/adoptium/temurin21-binaries/releases) 下载当前受支持 Java 21 的 ZIP 及对应 `.sha256.txt`（打包需要 Python 3.8+，无额外 Python 依赖）：

```sh
python scripts/package_portable.py --runtime-zip /path/to/official-windows-jre.zip --checksum-file /path/to/official-windows-jre.zip.sha256.txt
```

脚本校验 SHA-256，保留完整运行时许可证和第三方 notices，打包应用、示例和说明。已有输出时拒绝覆盖；请先移走旧包。官方 ZIP 和校验文件必须来自可信的同一发布版本，不能为了通过校验而自建校验文件。

## 测试与示例

`build.sh` / `build.cmd` 自动运行 **14 项测试**，失败时返回非零退出码，没有默认跳过项。

| 验证项 | 检查内容 |
| --- | --- |
| 几何与保留 | 4:3 → 16:9、原高度、顶层 x 平移、y 与宽高不变、组合子对象不重复移动 |
| 包结构 | 部件集合一致、图片/关系/嵌入工作簿字节一致，XML 除预期变化外节点和属性不变 |
| 版式继承 | 没有显式坐标的占位符继续继承，显式版式/母版坐标平移 |
| 字体 | 幻灯片、表格、图表、备注及主题字体声明替换，其他字体不变 |
| 不同输入 | 已有 16:9、非标准比例、损坏、Strict、签名、非法坐标、超大 XML、DTD/XXE |
| 扩展结构 | 替代内容选项各移动一次，运动路径数据保留并提示（合成 XML 单元样本） |
| HTTP 集成 | 上传、并发处理批次、失败隔离、单个/ZIP 下载、日志、中文/同名文件、访问保护、删除 |

示例位于 `examples/`：

- `sample-4x3.pptx`：两页，中文字体、图片、可编辑表格/图表、嵌入工作簿、组合形状、继承占位符、备注。
- `sample-16x9.pptx`：验证非 4:3 不改变尺寸。
- `invalid.pptx`：故意损坏的文件，用来验证批量失败隔离。

示例已包含在源码包，测试无需 Python。可选重新生成示例需要 `python-pptx` 和 Pillow：`python scripts/generate_examples.py`。

可选网页端到端测试：先启动服务（`--port 18123 --no-browser`），在已安装 Playwright 与 Chromium 的开发机运行 `node scripts/browser-smoke.cjs`。Linux 系统 Chromium 可使用 `CHROMIUM_PATH=/usr/bin/chromium`；它验证混合批次、下载、日志、新批次和手机布局。此测试依赖不进入应用或离线包。

实际 Windows 发布前建议人工验收：断开网络启动离线包；混合上传三个示例；检查成功 / 失败计数；在 PowerPoint 中打开输出，编辑图片、表格及图表；确认字体换行和母版位置；对自己的含动画 PPT 进行播放复查。当前 Linux 构建机已验证 Java 服务和 Chromium 页面，**未验证 Windows 双击行为及 PowerPoint 渲染**。

## 文件、日志与本机访问

- 单文件上限 128 MB；每批最多 20 个文件、累计上传不超过 256 MB；解压总量上限 512 MB；单个 PPT XML 上限 16 MB；包内最多 20,000 部件。
- 两个转换工作线程；单个失败不阻断后续文件。最多保留 8 个批次，完成后可用“开始新批次”删除旧批次。
- 输入和结果写入系统临时目录中的独立应用目录。转换后删除临时输入；下载不会自动删除结果。结果保留至删除批次、正常退出或 24 小时无访问后的定时清理。
- 日志记录检测尺寸、移动对象数、字体替换数、警告及失败原因，可单独下载；批次 ZIP 也附带日志。文件名可能包含私人信息，分享日志前请检查。
- 异常终止 / 断电可能留下 `ppt-format-converter-*` 临时目录，可在确认程序不运行后手动清理。应用不依赖远程 API，不上传云端。
- 页面及 API 只接受本机回环 Host，API 使用每次启动生成的令牌并拒绝跨来源请求；令牌不持久化到日志。其他本机用户或程序仍受操作系统访问权限约束。

## 项目结构

```text
src/main/java/com/pptconverter/
  PptxConverter.java       转换引擎、OOXML 坐标与字体处理
  App.java                 本机服务、批次任务、下载与生命周期
src/main/resources/web/   中文前端 HTML / CSS / JS
src/test/java/            无依赖单元及 HTTP 集成测试
examples/                 可运行示例与损坏输入
docs/ARCHITECTURE.md       架构、算法及维护说明
docs/Windows使用说明.txt    面向普通用户的启动说明
scripts/                  示例生成、离线运行时打包
build.sh / build.cmd      编译、打包、测试
Start.cmd / start.sh      启动入口
package-windows.cmd       Windows jpackage EXE 应用目录
```
