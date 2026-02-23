# M-Pesa Parser

Android app for parsing M-Pesa PDF statements into transaction data, categorizing activity, generating credit-risk insights, and exporting lender-ready reports.

## Features

- Parse password-protected M-Pesa statement PDFs (modern and legacy layouts).
- Detect duplicates and surface parse-quality diagnostics.
- Categorize transactions with built-in rules plus custom rule management.
- Analyze cashflow and credibility metrics for lending workflows.
- Generate and share reports in CSV and PDF formats.
- Save report history locally using Room.

## Tech Stack

- Kotlin + Jetpack Compose
- AndroidX Navigation, Lifecycle ViewModel
- Room (local persistence)
- PDFBox-Android (PDF parsing)
- Gradle Kotlin DSL

## Requirements

- Android Studio (recent stable)
- JDK 17 (required by Android Gradle Plugin 8.7.x)
- Android SDK:
  - `compileSdk = 35`
  - `targetSdk = 34`
  - `minSdk = 26`

## Getting Started

1. Open the project in Android Studio.
2. Ensure your `local.properties` points to a valid Android SDK.
3. Configure signing values for release builds (recommended: local-only or environment variables):
   - `MYAPP_UPLOAD_STORE_FILE`
   - `MYAPP_UPLOAD_KEY_ALIAS`
   - `MYAPP_UPLOAD_STORE_PASSWORD`
   - `MYAPP_UPLOAD_KEY_PASSWORD`
4. Sync Gradle.
5. Run the app on an emulator/device.

## Build and Test

From the project root:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

## How to Use

1. Open **Dashboard**.
2. Tap **Select M-Pesa Statement** and choose a PDF.
3. Enter the PDF password.
4. Review parsed transactions, diagnostics, and analytics.
5. Optional:
   - Refine categorization in **Rules**.
   - Filter/sort records in **Transactions**.
6. Generate and share reports from **Reports**:
   - **Download CSV**
   - **Download PDF**

## Data and Storage

- Parsing and analysis run on-device.
- Custom rules and scoring config are stored in SharedPreferences.
- Report history is stored in Room (`mpesa_parser.db`).
- Generated export files are written to app-specific external/internal storage and shared through `FileProvider`.

## Project Structure

- `app/src/main/java/com/mpesaparser/ui` - Compose screens/components/theme
- `app/src/main/java/com/mpesaparser/viewmodel` - app state and orchestration
- `app/src/main/java/com/mpesaparser/utils` - parsing, analytics, report generation
- `app/src/main/java/com/mpesaparser/data/local` - Room entities/DAO/repository
- `app/src/test/java/com/mpesaparser` - unit tests

## Notes

- If parsing fails, verify the PDF password and statement format.
- Keep release keystore credentials out of version control for production use.
