# RTRAB-C: Real-Time Risk Assessment in Baguio City Through Crowdsourced Data

## Project Overview
RTRAB-C is a mobile application developed for Baguio City to facilitate crowdsourced hazard reporting (e.g., floods, landslides, accidents) during severe weather conditions. The system features a local SQLite store-and-forward mechanism to allow citizens to capture reports offline during network outages, which automatically sync to a Supabase cloud server once connectivity is restored. It also includes a Moderator Dashboard for Local Government Units (LGUs) to verify incidents before they are published to a public risk map.

## Minimum System Requirements
*   **IDE:** Android Studio (Ladybug or newer recommended)
*   **Target OS:** Android 7.0 (Nougat) / API Level 24 or higher
*   **Hardware:** Active GPS/Location Services and Camera capabilities
*   **Internet:** Required for initial map tile rendering and cloud synchronization

## Setup and Installation Instructions
1.  **Extract the Project:** Unzip the provided `RTRAB_C_Defense_Build.zip` file into a local directory on your computer.
2.  **Open in Android Studio:** Launch Android Studio, select "Open an Existing Project," and select the extracted folder.
3.  **Sync Dependencies:** Allow Gradle to download all required packages (Supabase, OSMDroid, AndroidX Room). This requires an active internet connection.
4.  **Connect a Device:** Connect a physical Android device via USB (with Developer Options and USB Debugging enabled) or launch an Android Virtual Device (AVD).
5.  **Run the Application:** Click the "Run" button (Shift + F10) in Android Studio to compile and install the application onto the device.
6.  **Quick Install (Alternative):** A pre-compiled `RTRAB-C_Final_Build.apk` is included in the root folder. You may transfer this directly to an Android device to install the app without using Android Studio.

## Test Accounts
To evaluate the system's Role-Based Access Control (RBAC) and validation logic, please use the following predefined credentials:

**Moderator (LGU) Account:**
*   **Email:** admin_lgu
*   **Password:** ADMIN****
*   **Passcode (for registration testing):** RtrabcPhenix
*   *Access Rights:* Full access to the tactical map, incident verification queue, user audit logs, and data analytics export.

**Citizen User Account:**
*   **Email:** user@gmail.com
*   **Password:** User1234
*   *Access Rights:* Hazard reporting, offline caching, upvoting/verification, and live risk map viewing.

## Key Technologies Used
*   **Language:** Kotlin
*   **Backend:** Supabase (PostgreSQL, Authentication, Cloud Storage)
*   **Local Cache:** SQLite (AndroidX Room)
*   **Mapping:** OpenStreetMap (OSMDroid)
