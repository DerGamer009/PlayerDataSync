# PlayerDataSync Metrics Documentation

This document provides the technical details for setting up the **bStats** and **FastStats** dashboards.

---

## 📊 bStats (Classic Metrics)
**Plugin ID:** `25037`

To make the data appear on the bStats website, create the following charts in your dashboard using these exact settings:

### 1. Database Distribution
*   **Chart Type:** `Simple pie`
*   **Chart Title:** `Database Type`
*   **Chart ID:** `database_type`

### 2. NMS Version Wrapper
*   **Chart Type:** `Simple pie`
*   **Chart Title:** `Active NMS Handler`
*   **Chart ID:** `nms_version`

### 3. Performance: Global Save Time
*   **Chart Type:** `Single line chart`
*   **Chart Title:** `Average Save Time`
*   **Chart ID:** `avg_save_time`
*   **Line Name:** `ms`

### 4. Sync Feature Adoption
*   **Chart Type:** `Simple pie`
*   **Chart IDs:**
    *   `sync_coordinates`
    *   `sync_inventory`
    *   `sync_xp`
    *   ... (and others)

---

## ⚡ FastStats (Modern Metrics)
**Project Token:** `cfc414c1c6ad3d95ed350dae82d82ced`

FastStats tracks high-frequency data and requires Java 17+.

### Implemented Data Points
| ID (UUID) | Type | Description |
|---|---|---|
| `471bbb36-559b-4f95-8579-4c41d201e161` | String | Database Type |
| `2e87b144-e9f4-4020-8a85-6e3af7ed68b6` | String | Active NMS Handler |
| `2ee1e7dd-5ee8-47ff-8a57-0dff9682e49d` | Number | Average Save Time (ms) |
| `sync_[feature]` | Boolean | Individual toggles for all sync features |

---

## 🔐 Privacy & Safety
- **Buffered Sending**: Data is buffered to prevent impact on server performance.
- **Java 8 Safety**: FastStats is automatically isolated on older versions to prevent crashes on Minecraft 1.8.
- **Anonymous**: No sensitive player data (IPs, Names) is ever transmitted.
