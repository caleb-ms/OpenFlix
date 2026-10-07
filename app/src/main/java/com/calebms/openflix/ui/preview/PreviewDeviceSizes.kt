package com.calebms.openflix.ui.preview

import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview

/**
 * Custom Multi-Preview annotation that renders the annotated Composable
 * across 5 key device form factors in Android Studio's Compose Preview tab:
 * 1. Compact Phone (Portrait)
 * 2. Phone Landscape
 * 3. Foldable (Inner Screen)
 * 4. Tablet (10-inch)
 * 5. Desktop / Large Display
 */
@Preview(name = "1. Phone - Portrait", device = Devices.PHONE, showBackground = true)
@Preview(name = "2. Phone - Landscape", device = "spec:width=891dp,height=411dp,orientation=landscape", showBackground = true)
@Preview(name = "3. Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "4. Tablet (10\")", device = Devices.TABLET, showBackground = true)
@Preview(name = "5. Desktop", device = Devices.DESKTOP, showBackground = true)
annotation class PreviewDeviceSizes
