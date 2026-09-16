# Pixel IMS 5G+

[中文说明](README.zh-CN.md)

Adds the **5G+ / NR_ADVANCED** icon and IMS-related carrier switches on Pixel phones for domestic Sub-6.

**Shizuku is not required. Root is not required.** Privilege comes from the system Wireless debugging setting: pair once on loopback, then write carrier config. There is no launcher icon; the app looks like a system component.

If 5G+ is already in effect, turning Wireless debugging on does not prompt again. Falling back to LTE indoors also does not.

## Usage

1. Install the APK, then **reboot once**.
2. Enable Developer options → **Wireless debugging** (USB debugging alone is not enough).
3. Follow the notification:
   - Not paired yet: open the system “Pair with device” page. A notification input appears; type the 6-digit pairing code.
   - This `Ims` device is already remembered: the notification asks whether to apply the 5G+ patch. Tap Apply.
4. After a successful apply, the release build turns Wireless debugging and USB debugging off (and Developer options, when the system allows it). The waiting notification goes away.

You only need this flow again if the patch is gone (OTA, a new SIM, and similar). Do not clear app data, or you will have to pair again. If Wireless debugging lists more than one `Ims`, forget the old entries.

Notification permission must be allowed, or pairing / Apply prompts will not show.

## Inspiration and references

This repository is a fork, not an IMS tool written from scratch. Credit belongs with the original authors:

| Source | Contribution |
|---|---|
| [vvb2060/Ims](https://github.com/vvb2060/Ims) | Base project: IMS / carrier-config overrides via instrumentation |
| [TakaiSaisei/pixel_ims](https://github.com/TakaiSaisei/pixel_ims) | Wireless-debugging privilege path that removes the Shizuku dependency; portions of that implementation |
| [ryfineZ/carrier-ims-for-pixel](https://github.com/ryfineZ/carrier-ims-for-pixel) | 5G+ carrier-config keys (icon, bandwidth threshold, NR bands) |
| [FlyfishXu/kadb](https://github.com/FlyfishXu/kadb) | Loopback Wireless debugging TLS pairing and connection |

## What the patch writes

- `5g_icon_configuration_string`
- `nr_advanced_threshold_bandwidth_khz_int`
- `additional_nr_advanced_bands_int_array`
- `nr_advanced_capable_pco_id_int`
- `include_lte_for_nr_advanced_threshold_bandwidth_bool`

plus the original vvb2060/Ims VoLTE / VoNR / VoWiFi / VT / UT overrides.
