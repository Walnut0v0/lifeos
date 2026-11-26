# 本地项目位置

正式工作根目录：D:/document/10.others/lifeos。

- android/：完整Android源代码、Gradle工程与本机构建缓存。
- .git/：保留所有原分支、备份分支和提交历史；远端仍为Walnut0v0/lifeos。
- work/toolchain/：原Android SDK、Gradle8.13、Gradle依赖缓存、已验证的cloudflared（开发通道可选）。
- work/history/：旧构建记录和开发脚本；仅本机保留。
- work/private/：私有服务运行数据、随机凭证；不进入GitHub。
- outputs/：安装包、哈希和用户说明；不进入Git，GitHub Actions另行生成artifact。
- bridge/：私有交换服务源码、接口描述及合成数据测试。

在根目录运行 ./tools/build.ps1 构建两个变体。系统Java来自Android Studio的JBR21，脚本不修改全局JAVA_HOME。新机器需安装JDK21并准备SDK/Gradle，或按CI使用android/gradlew。

迁移保留现有手机数据与签名；没有修改系统Android Studio安装、用户全局Java配置或其他项目。原生成聊天文件夹中的聊天元数据归Codex所有，不属于工程。

旧聊天目录仅保留outputs兼容链接，指向D盘outputs，用于让以前聊天里的文件链接继续可用；无工程或交付物副本。
