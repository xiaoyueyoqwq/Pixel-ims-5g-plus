# Pixel IMS 5G+

Minimal Pixel IMS patch build with `5G+ / NR_ADVANCED` icon support for domestic Sub-6 bands.

## Base

- Base project: `vvb2060/Ims`
- Upstream: https://github.com/vvb2060/Ims

## 5G+ Patch Attribution

- The `5G+` carrier-config patch in this fork is adapted from the 5G+ work published in `ryfineZ/carrier-ims-for-pixel`.
- Patch source / attribution: https://github.com/ryfineZ/carrier-ims-for-pixel

This fork keeps the `vvb2060/Ims` base and adds the minimal carrier-config changes needed to expose `5G+` behavior on supported domestic Sub-6 NR bands.

## What Changed

- Added `5g_icon_configuration_string`
- Added `nr_advanced_threshold_bandwidth_khz_int`
- Added `additional_nr_advanced_bands_int_array`
- Added `nr_advanced_capable_pco_id_int`
- Added `include_lte_for_nr_advanced_threshold_bandwidth_bool`

## Notes

- This repo is intended as a minimal fork, not a full carry-over of the `ryfineZ` app-side feature set.
- All credit for the original base and the original 5G+ patch idea remains with their respective upstream authors.
