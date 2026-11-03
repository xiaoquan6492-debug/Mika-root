# Mika-root：非官方 ROOT 增强分支

**状态：源码优先预览；本仓库尚未发布 APK Release。**

英文 README 与五份译文都带有等效的分支/源码优先提示；本文仍是本分支的权威说明。

Mika-root 是 [Mika](https://github.com/wangzan101/Mika) 的**非官方**分支，维护仓库是
[xiaoquan6492-debug/Mika-root](https://github.com/xiaoquan6492-debug/Mika-root)。它不隶属于上游作者，也未获上游背书或支持。上游项目、发布下载、更新服务、社区渠道和任何捐赠入口都属于上游 Mika，不属于本分支。

本文说明 ROOT 功能做什么、**不**保护什么、这些代码从哪里来，以及发布二进制之前还必须完成什么。

## 与上游的差异

本分支从上游 Mika **0.1.34（versionCode 35）**、提交
`4ce3ca67e8cf8af8323a4d74505f51fd94178137` 出发。当前验收树加入源自上游 `feat/root-mode` 分支、
标签 `v0.1.33-root`（`fb54ce9f1e76a28ede7beb6383847e610c97886a`）的 ROOT 功能，并应用了经审查的安全修正。
同时包含四个已审查的上游 PR 修复：

| PR | 使用的 head SHA | 作者 | 本树包含的改动 |
|---|---|---|---|
| [#233](https://github.com/wangzan101/Mika/pull/233) | `1a3c97c1783387d483ba3d0650a505708a13fcc6` | destire-mio | 在整数溢出绕过长度检查之前拒绝 chunked HTTP 请求体 |
| [#258](https://github.com/wangzan101/Mika/pull/258) | `09ddb1bf3facc6b0b870114cdb8b3625031e4492` | CatCatUncle | 先校验完整子任务 JSON 再截断；落盘只保留有界预览 |
| [#255](https://github.com/wangzan101/Mika/pull/255) | `6d242ab859d1a6c993b575a319713bc2eccaf560` | kkkaqi | 在回调路径读取并关闭 MCP HTTP 正文；保留取消传播与 DELETE 认证 |
| [#242](https://github.com/wangzan101/Mika/pull/242) | `c33c2485b158cfd0547d781f8c796bf8c59d3c32` | 15026087259ml-afk | 在模型列表未声明档位时加入 DeepSeek 官方 `none/low/high/max` 思考档位 |

ROOT 功能不是本分支维护者的单独原创。root-mode 分支及其提交属于上游工作；本分支保留署名，只增加经审查的整合和文档。

## 两个 ROOT 开关

在已 root 的手机上，**设置 › 手机操作 › Root** 显示两个互相独立的开关。它们都默认**关闭**，且只有找得到
`su` 时才显示。打开一个不会授权另一个。

1. **用 root 启动手机助手**：不经过无线调试或电脑，启动同一个内置助手。助手经 `su 2000 -c` 启动，以
   **UID 2000（shell）**运行，而不是 UID 0。App 读到 `id -u == 2000` 后才交出一次性 nonce；原有的
   UID/PID/Binder/APK/签名/口令交接校验保留。App 本身和助手都不会变成 UID 0 进程。
2. **允许 Mika 执行 root 命令**：启用 `root_run_command`。该命令通过 `su` 以真正的 root 权限执行。它与
   启动助手是两个独立开关，必须单独打开。

关闭 root 命令开关会停止新的 root 命令；解除手机操作配对会同时关闭两个开关并清除授权结果。`su` 拒绝或
无回应时不会循环弹窗；设置页会说明结果并提供显式重试。

## `root_run_command`：审批与可用范围

工具接受一条命令（最长 4,000 字符）和超时（1–300 秒，默认 30 秒）。stdout 与 stderr 分开读取，**每个流最多
保留 1 MiB**；超出的字节计入丢弃，工具返回结果也可能再被裁剪。NUL 字符直接拒绝。root 命令跨任务全局串行：
第二个任务等待第一个，等待可取消；拿到锁后会再次检查开关、授权和任务来源。

**询问模式：** 每一条 root 命令——**包括只读命令**——都先显示**完整命令**，并要求对这一条一次性确认。
确认只能使用一次；设置变化、命令变化或确认过期都不能复用。

**自主模式：** 只有同时满足以下条件，root 命令才可能不弹确认：

- 用户明确打开了 **允许 Mika 执行 root 命令**；
- 任务是明确自主、有人看着的 **UI** 任务；
- 该任务确实需要这条命令。

非 UI 来源、无人值守/定时任务、子任务和条件检查不会获得 root 端口。端口层在每次执行前实时复查开关、
`su` 授权、任务来源和在场状态；用户撤权后，任务不能继续使用旧的正向授权。

这些机制不是让 root 变成安全工具。命令可以修改或删除 root 能触及的任何数据、读取其他 App 的私有数据、
改系统设置或启动常驻进程。只在你自己控制的手机上使用；不需要时保持关闭；敏感操作使用询问模式并检查完整命令。

## 这些机制不保护什么

- **root 命令不保留普通 App/沙箱边界。** 它在 Android App 沙箱之外、也在 PRoot Linux 沙箱之外运行，能触及
  普通沙箱刻意隔离的数据和系统区域。
- **手机工具的 `su` 检查是词法提示，不是安全沙箱。** 常规 `phone_system_command` 中明显的 `su` 会被拒绝并
  引导到 `root_run_command`，但编码、间接或外部提权方式没有被完整阻断。不能据此声称普通 shell 安全。
- **进程清理是有界的，不是绝对的。** 取消或超时会关闭流并终止 Mika 直接启动的 `su` 进程；detached 孙进程或
  仍持有管道的进程可能继续存在，守护 reader 线程可能仍阻塞在管道上；不能保证所有后代/脱离进程都结束。
- **root 管理器仍是信任边界的一部分。** 是否授权 su、root 管理器自身的策略，不由本 App 完全控制。
- **没有暗示安全审计。** 下列测试是功能和回归测试，不是渗透测试，也不能证明 root 命令安全。

## 构建、版本与验证证据

本地验收树使用 **JDK 17**、Android SDK **compileSdk 36** 和项目自带的 Gradle wrapper 构建。版本仍是
**0.1.34 / versionCode 35**，不是新的发布版本。

可用证据有限：

- 本地组合目标测试共 **286 个通过**（core 224、app 62；显式过滤器内 0 失败/错误/跳过）。
- 协调者独立复跑 **83 个关键测试**，同样 0 失败/错误/跳过。两组有重叠，**不能相加成 369 个独立测试**。
- 更早的全量运行记录了 **135 个失败/错误**，未完成同环境 HEAD 基线归因；之后没有重跑全量，**全量不是绿色**。
  不能把定向结果说成全量覆盖。
- 维护者/用户报告真机验收通过。**没有公开日志、截图、设备标识、凭据或独立见证的测试矩阵。** 这是对维护者/用户
  陈述的转述，不是本仓库独立验证的结论。
- 没有声称公开 APK Release、真机安装矩阵、WebView 矩阵或线上模型/API 验证。

## 安装与签名警告

applicationId 仍是 **`com.wangzan101.mika`**，即上游包名。仓库内的 Gradle 配置没有定义 release 签名身份；
本地 debug/perf 构建使用开发者本机 debug keystore，而上游正式版使用不同密钥。因此：

- 同包名的 debug 构建不能和官方 App 并排安装；
- 用另一把密钥签名的构建不能覆盖官方 App；为解决签名不匹配而卸载，可能丢失 App 数据；
- 从上游继承的「关于 › 检查更新」仍访问**上游** `wangzan101/Mika` 的 Release API，不是本分支；不能假定它会提供
  兼容本分支的构建。

未来若要分发本分支，必须明确设计发布签名和更新策略。当前源码优先仓库不发布 APK，也没有 Release tag。

## 许可证与源码可得性

上游仓库的 **Apache-2.0 `LICENSE`**、**`NOTICE`**、版权署名和全部第三方许可证原文都原样保留；本分支不对上游或
第三方代码重新授权。请阅读 [`LICENSE`](../LICENSE)、[`NOTICE`](../NOTICE) 和
[`licenses/THIRD_PARTY_NOTICES.md`](../licenses/THIRD_PARTY_NOTICES.md)。

源码树仍包含继承的 native 与 rootfs 资产，因此**并非全部是 Apache-2.0**。第三方声明包括：

- PRoot 5.4.0，GPL-2.0-or-later，含四个已跟踪补丁，编译为 `libproot.so` 和 `libproot_loader.so`；
- talloc 2.4.3，LGPL-3.0-or-later，静态链接进 PRoot 可执行文件；
- Alpine 3.22.7 运行环境及其 Python、Node、Pillow、pypdf 等软件包，各依其自身许可证（GPL、LGPL、MIT、BSD、
  MPL 等）。

源码锁定和构建配方保留在 [`tools/sandbox/`](../tools/sandbox/README.md)：`sources.lock`、
`runtime/sources.lock`、补丁、`build-native.sh`、`build-runtime.py`、`runtime-sources.py`。第三方声明要求：
**发布 APK 二进制时，必须在同一发布页提供对应的 `mika-<version>-gpl-sources.tar.gz`。**

**对应源码状态：** 本 checkout 包含完整并已校验的源码包
`mika-0.1.34-gpl-sources.tar.gz`（111,367,173 字节；SHA-256
`98d5c00805e13a89599ba94feaa50849602421e79294534a916e29fc0c43b538`）。其中包含 PRoot/talloc 原始源码包、四个补丁与
构建/重链接脚本；Alpine copyleft 配方、上游源码、来自固定提交的 11 个 install/trigger 脚本及
`SOURCES.txt`/`SOURCE-EXTRA.tsv` 索引；完整 27 个 crate 的 Cargo.lock 依赖闭包及许可证和 `make-vendor.py`；以及保留的
许可证/声明文本。已校验的完整包分成三个有序分片，位于
[`third_party/corresponding-sources/0.1.34/`](../third_party/corresponding-sources/0.1.34/README.md)。
**这些分片与哈希都包含在本源码树中。** 取得本 checkout 后，请按该目录的 README 拼接并校验完整包。本文**不**声称
经过独立见证的公开托管或法律保证，本源码树也不包含 APK Release。这里不虚构源码提供联系人。

## 上游、社区与捐赠

发布、更新、Issue、社区和任何捐赠机制都请使用上游 Mika。本分支没有单独的 Release、捐赠或社区计划。README 中
保留的上游链接用于署名和方便访问，不代表本分支就是上游项目，也不代表捐赠会流向本分支。

本分支特有的 ROOT 集成问题请提交到[本分支的 Issues](https://github.com/xiaoquan6492-debug/Mika-root/issues)，这**不是**上游 Mika 的支持义务。官方上游发布、更新服务、社区渠道和捐赠仍属于上游。

## 来源摘要

- 上游基线：`wangzan101/Mika`，提交 `4ce3ca67e8cf8af8323a4d74505f51fd94178137`，版本 0.1.34 / code 35。
- ROOT 来源：上游 `feat/root-mode`、标签 `v0.1.33-root`、提交
  `fb54ce9f1e76a28ede7beb6383847e610c97886a`；经审查的安全修正后适配。
- 已审查 PR 与作者：见本文顶部表格；链接均指向上游 Pull Request。
- 逐文件适配/声明清单：[docs/fork-modifications.md](fork-modifications.md)。
- 本分支：[xiaoquan6492-debug/Mika-root](https://github.com/xiaoquan6492-debug/Mika-root)。
- 本分支不声称 root-mode 代码或四个 PR 改动由本分支维护者单独原创。
