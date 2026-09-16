# Pixel IMS 5G+

[English](README.md)

给 Pixel 补上国内 Sub-6 上的 **5G+ / NR_ADVANCED** 图标和 IMS 相关开关。

**不需要 Shizuku，也不需要 root。** 权限来自系统自带的「无线调试」：在本机 loopback 上配对一次，再写入运营商配置。没有桌面图标，看起来像系统组件。

5G+ 已经生效时，打开无线调试也不会再打扰你。室内掉回 LTE 同样不会反复弹窗。

## 下载

签名 APK 在 [Releases](https://github.com/xiaoyueyoqwq/Pixel-ims-5g-plus/releases)。每次推到 `master` 会更新当前版本号对应的 Release。

## 使用

1. 安装 APK，**重启一次**。
2. 打开开发者选项 → **无线调试**（只开 USB 调试不够）。
3. 按通知操作：
   - 还没配对：系统「与设备配对」页打开后，通知里会出现输入框。填入 6 位配对码。
   - 已经记住这台 `Ims` 设备：通知会直接问是否写入 5G+ 补丁，点 Apply。
4. 成功后会有一条通知告诉你补丁已写入。确认无误后你可以自行选择是否保持开发者模式是否继续开启，这不会影响后续补丁运行。

通知权限需要允许，否则看不到配对 / Apply 提示。

## 边界

设备重启后补丁会稳定丢失，这个问题无法避免。因此每次重启之后都需要重新走一遍流程、再应用一次补丁。

好消息是：Ims 会主动探测 Android 历史无线调试设备列表。已经记住这台 `Ims` 时，打开无线调试后只需在通知栏确认是否应用补丁，不必再输入配对码。不要清掉应用数据，否则要重新配对；无线调试列表里如果出现多个 `Ims`，把旧的忘掉即可。

## 灵感与参考

本仓库是 fork，不是从零写的 IMS 工具。上游与参考如下，完整功劳归原作者：

| 来源 | 贡献内容 |
|---|---|
| [vvb2060/Ims](https://github.com/vvb2060/Ims) | 基础工程：通过 instrumentation 覆盖 IMS / 运营商配置 |
| [TakaiSaisei/pixel_ims](https://github.com/TakaiSaisei/pixel_ims) | 经无线调试完成本机提权、从而不依赖 Shizuku 的路径，以及其中部分实现 |
| [ryfineZ/carrier-ims-for-pixel](https://github.com/ryfineZ/carrier-ims-for-pixel) | 5G+ 运营商配置键（图标、带宽门限、NR 频段） |
| [FlyfishXu/kadb](https://github.com/FlyfishXu/kadb) | 本机无线调试的 TLS 配对与连接 |

## 补丁会写什么

- `5g_icon_configuration_string`
- `nr_advanced_threshold_bandwidth_khz_int`
- `additional_nr_advanced_bands_int_array`
- `nr_advanced_capable_pco_id_int`
- `include_lte_for_nr_advanced_threshold_bandwidth_bool`

以及上游 vvb2060/Ims 原有的 VoLTE / VoNR / VoWiFi / VT / UT 覆盖。
