# 狐狐魔金小工具（FoxFoxM3Tools）

魔法金属的客户端便捷工具套件。当前版本 **1.7.0**，适用于 **Minecraft 1.7.10 / Forge**。

- 作者：**白狐铃**
- Credits：白狐铃自己用的小工具套件
- Mod ID：`foxfoxm3tools`
- 安装位置：客户端；服务器无需安装本小工具。

## 功能

| 功能 | 说明 |
| --- | --- |
| 自动裁缝、酿造、烹饪、锻造 | 在原小游戏规则下自动操作；裁缝持续运行至倒计时结束，支持同一界面连续制作。 |
| 自动考古 | 按覆盖层选择工具，完成后领取古物并归还工具。 |
| 自动撬锁 | 自动开始本轮，在合适时机操作；成功或失败后停止。 |
| 奖励箱与圣器自动领奖 | 自动关闭本次打开的领奖界面，剩余奖励按原规则掉到脚下。 |
| 灵力自动重置 | 按指定阶段和目标品质持续重置，直到品质精确匹配。 |
| 物品提示分栏 | 自动换行、分栏，空间不足时支持翻页。 |
| 蜂箱快捷按钮 | 手动丢出产物或从背包补充普通蜜蜂。 |
| 魔王快捷召唤 | 保存 Boss 后，手持魔力水晶右键召唤台即可快捷召唤。 |
| 地下城重置卷自动关界面 | 手持冷却时间重置卷右键入口时，自动略过入口界面。 |
| 组合技能栏一键释放 | 两个可自定义快捷键分别释放两行组合技能栏，各最多 7 格；跳过空位和同一行重复技能，保留原施法条件。 |

详细操作、配置和安装方法见 [使用说明](使用说明.md)。

1.7.0 新增的两个技能栏快捷键默认未绑定。在 **选项 → 控制 → 狐狐魔金小工具** 中分别设置后，关闭界面即可使用；长按不重复施放，冷却、耗魔、武器和已学技能等限制沿用原魔法金属规则。

## 运行依赖

- Minecraft 1.7.10 与对应 Forge。
- 魔法金属 `manametalmod`：当前针对 **8.0.7** 开发并验证。
- **Muya 1.11.1**：魔法金属 8.0.7 的必需前置。

FoxFoxFix 可同时安装。替换旧版时只保留一个 FoxFoxM3Tools JAR，移出已合并的独立小工具。

## 工程结构

```text
src/            模组 Java 源码、语言文件和 mcmod.info
tests/          无需启动游戏的构建回归检查
client-tests/   隔离客户端验证源码与启动脚本
server-tests/   蜂箱、魔王召唤和组合技能栏的服务端回放验证
project.json    版本、模组入口与测试组件配置
build.py        编译、构建回归和生成 JAR
build.ps1       PowerShell 构建入口
package.py      核验发布记录并生成含源码的发布包
使用说明.md      完整功能与验证说明
```

此仓库保存源码和构建所需脚本。构建产物、运行目录、备份、IDE 配置及第三方游戏文件由 `.gitignore` 排除。

## 构建

需要 **Python 3.9 或更高版本**，以及提供 `java`、`javac` 命令的 JDK。现有构建环境使用 **JDK 21**，输出为 **Java 8 字节码**。构建脚本使用 `javac --release 8`。

第三方依赖在仓库外准备。脚本通过 `FOXFOX_WORKSPACE` 找到依赖工作区；未设置时使用本仓库的父目录。默认布局如下：

```text
<依赖工作区>/
  .minecraft/
    libraries/
      org/ow2/asm/asm-all/5.0.3/asm-all-5.0.3.jar
      net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar
      org/lwjgl/lwjgl/lwjgl/2.9.1/lwjgl-2.9.1.jar
    mods/[Muya]Muya_1.11.1.jar
  fcwqmmmserver/mods/manametalmod-8.0.7.jar
  FoxFoxAccessories/tools/forge-1.7.10-srg.jar
```

`forge-1.7.10-srg.jar` 是包含 Minecraft/Forge 类及 SRG 方法名的编译依赖；普通 Forge 安装器不能直接代替。

在本仓库目录执行，按实际位置设置依赖工作区：

```powershell
$env:FOXFOX_WORKSPACE = 'D:\MinecraftWorkspace'
python build.py
```

也可以使用 `build.ps1`。如三个模组／编译依赖的位置不同，可单独指定：

```powershell
$env:FORGE_SRG_JAR = 'D:\MinecraftDependencies\forge-1.7.10-srg.jar'
$env:MANAMETAL_JAR = 'D:\MinecraftDependencies\manametalmod-8.0.7.jar'
$env:MUYA_JAR = 'D:\MinecraftDependencies\Muya_1.11.1.jar'
python build.py
```

ASM、LaunchWrapper 和 LWJGL 仍从 `FOXFOX_WORKSPACE` 下的 `.minecraft/libraries` 读取。成功后生成 `dist/FoxFoxM3Tools-1.7.10-1.7.0.jar`，构建记录写入 `build/`。

## 隔离运行验证与发布包

`python build.py` 已包含无需打开游戏的回归检查。完整运行验证还需要在依赖工作区准备现有 Java 21 客户端启动配置、游戏资源与测试服务端模板：

- `.minecraft` 下的运行依赖及 `assets`。
- `fcwqmmmserver/deployment/java21/prepared-client-version.json` 和 `client-classpath.json`，其中路径应指向本机资源。
- `FoxFoxAccessories/test-server` 下的 Crucible/Forge 测试服务端、配置与依赖。
- 使用 `--with-peer` 时，需要 `FoxFoxFix/dist/FoxFoxFix-1.7.10-1.0.0.jar`。

这些本地运行环境不随源码分发。准备好后执行：

```powershell
python client-tests/run.py all --with-peer
python server-tests/run-beehive.py
python server-tests/run-bosssummon.py
python server-tests/run-skillrow.py
python package.py
```

省略 `--with-peer` 可单独验证小工具。**所有测试客户端必须保持静音**：启动脚本在启动前将总音量和全部声音分类设为零，并在运行时核验。测试输出仅写入 `build/`。

`package.py` 会核验当前 JAR 与各项测试记录的哈希，检查通过后生成 `dist/*-with-source.zip` 和 `dist/SHA256SUMS.txt`。将生成的 JAR 或 ZIP 作为发布附件上传即可；源码提交由 `.gitignore` 自动排除这些输出。
