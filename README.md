# AcadLink — Academic Project Hub & AI Similarity and AI Content Checker

An Android app that helps students **upload, browse, bookmark, and review academic projects**. It includes a lightweight **AI-assisted similarity check** that compares a new submission’s *title*, *abstract*, and *methodology* against the existing corpus stored in Firebase — flagging potential overlaps before publication.

> **Tech Stack:** Android (Java), Gradle (Kotlin DSL), Firebase (Auth, Realtime Database, Storage), Material Components, AndroidX.

---

## ✨ Features

- **Authentication**
    - Email/Password Sign-In via Firebase Auth.
- **Project Uploads**
    - Submit project metadata: title, project type/level, abstract, methodology, and file attachments.
- **Explore All Projects**
    - Scrollable list of all community projects with quick detail views and summaries.
- **My Projects**
    - View your own submissions; open full details and summaries.
- **Bookmarks**
    - Save projects you want to revisit in **My Bookmarks**.
- **Project Details & Summaries**
    - Rich details screens (e.g., *AllProjectSummary*, *MyProjectSummary*).
- **AI Checking**
    - Computes a weighted Jaccard similarity across **title (30%)**, **abstract (40%)**, and **methodology (30%)** to flag potential overlaps.
- **Basic Chats (scaffold)**
    - Placeholder fragment exists for future collaboration features.
- **Crash reporting (optional)**
    - Firebase Crashlytics wired in via Gradle plugin.

---

## 🏗️ Architecture & Modules

- **Language:** Java (Android).
- **Build:** Gradle Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`, `libs.versions.toml`).
- **Firebase:**
    - **Auth:** Email/Password, optionally Google Sign-In.
    - **Realtime Database:** Project records under the `projects` node.
    - **Storage:** For uploaded files (e.g., PDFs, reports).
- **UI:** AndroidX + Material components. Bottom navigation with Fragments:
    - `HomeFragment`, `ChatsFragment`, `AccountFragment`, `MyBookMarkFragment`.
- **Screens / Activities (highlights):**
    - `MainActivity` (nav host), `UploadProjectActivity`, `ProjectDetailsActivity`,
      `AllProjectsActivity`, `AllProjectSummaryActivity`, `MyProjectSummaryActivity`,
      `AiCheckingActivity`, `LoginOptionsActivity`, `LoginEmailActivity`, `RegisterEmailActivity`,
      `ProfileEditActivity`, `ForgetPasswordActivity`, `DeleteAccountActivity`.
- **Data Model:** `ProjectModel` (id, title, projectType1/projectLevel, abstractText, methodology, similarity, aiGenerated, fileInfoList).

Project package: `com.example.acadlink` (see `app/src/main/java/com/example/acadlink`).

---

## 🧠 Similarity Algorithm (AI Checking)

`AiCheckingActivity` loads existing projects from **Firebase Realtime Database** and computes a composite similarity using **Jaccard** over token sets (with light stopword removal).
- **Weights:** title = **0.30**, abstract = **0.40**, methodology = **0.30**.
- **Tokenization:** lowercase, split on non-word characters, ignore 1‑char tokens and common stopwords.
- **Output:** a similarity score (0–1) used to flag possibly overlapping submissions.

> See `AiCheckingActivity.java` for `computeCompositeSimilarity`, `jaccardSimilarity`, and `tokenizeAndFilter` implementations.

---

## 📁 Project Structure

```
AcadLink/
├─ app/
│  ├─ src/main/
│  │  ├─ AndroidManifest.xml
│  │  ├─ java/com/example/acadlink/
│  │  │  ├─ MainActivity.java
│  │  │  ├─ (Activities) UploadProjectActivity, ProjectDetailsActivity, ...
│  │  │  ├─ (Fragments) HomeFragment, AccountFragment, ChatsFragment, MyBookMarkFragment
│  │  │  ├─ ProjectModel.java, MyProjectsAdapter.java, Utils.java, ...
│  │  └─ res/values/strings.xml
│  ├─ build.gradle.kts
├─ build.gradle.kts
├─ gradle.properties
└─ gradle/libs.versions.toml
```

---

## 🔧 Getting Started

### Prerequisites
- **Android Studio** (Ladybug/Koala or newer)
- **JDK 17** (required by Android Gradle Plugin 8.9.1)
- A **Firebase** project

### 1) Clone & Open
```bash
git clone <your-fork-or-repo-url>.git
cd AcadLink
# Open the `AcadLink` project directory in Android Studio
```

### 2) Configure Firebase
1. Create a Firebase project and **add an Android app** with the package `com.example.acadlink`.
2. Download **`google-services.json`** and place it at:  
   `AcadLink/app/google-services.json`
3. In **Firebase Console** enable:
    - **Authentication** → Email/Password (and Google Sign-In if you want)
    - **Realtime Database** → create database (in *test mode* for development)
    - **Storage** → create default bucket (in *test mode* for development)
4. (Optional) **Crashlytics**: enable crash reporting for release builds.

> **Note:** `strings.xml` contains a `web_client_id` used for Google Sign-In; update with your OAuth client ID if you enable Google auth.

### 3) Build & Run
- Sync Gradle, let dependencies resolve.
- Select a device (minSdk **24**, target/compile **35**) and run.

---

## 🗄️ Data Model & Realtime Database

A typical project entry in Realtime Database (`projects/<projectId>`) includes:
```json
{
  "id": "auto-generated",
  "title": "IoT-based Smart Farming",
  "projectType1": "IoT",
  "projectLevel": "Undergraduate",
  "abstract": "…",
  "methodology": "…",
  "similarity": "0.18",
  "aiGenerated": "false",
  "fileInfoList": ["gs://<bucket>/projects/<id>/report.pdf"]
}
```

> Uploads may store files in Firebase **Storage**, referenced by `fileInfoList`. The app reads/writes under the `projects` node and uses `ProjectModel` to map entries.

---

## 🔐 Security & Privacy

- **Do not ship** test DB/Storage rules to production.
- In production, set Firebase Realtime Database rules to ensure only the owner can write their projects and public read is limited as required.
- Validate max lengths and sanitize user content (title/abstract/methodology) server-side if you add a backend.
- If you keep Crashlytics, disclose it in your privacy policy.

---

## 🧩 Configuration

Key values to confirm:
- `applicationId`: `com.example.acadlink` (in `app/build.gradle.kts`)
- `compileSdk`: **35**; `minSdk`: **24**
- `strings.xml` → `web_client_id` (update if using Google Sign-In)
- `google-services.json` in `app/`

---

## 🧪 Testing

- Unit tests: add JUnit tests for tokenization and `jaccardSimilarity` to lock the similarity behavior.
- Instrumentation tests: verify upload/browse flows.
- Manual checks:
    - Upload a sample project, verify it appears in **All Projects** and **My Projects**.
    - Bookmark from project list, confirm it appears in **My Bookmarks**.
    - Run **AI Checking** before/after upload to see similarity scores change.

---

## 🛣️ Roadmap

- [ ] Full chat & collaboration flow
- [ ] Search & filters for projects
- [ ] Rich file previews (PDF viewer)
- [ ] Admin/moderation tools
- [ ] Improved AI (semantic embeddings / cosine similarity)

---

## 🤝 Contributing

1. Fork the repo and create a feature branch.
2. Commit with conventional messages (e.g., `feat:`, `fix:`).
3. Open a PR describing the change and testing steps.

---

## 📄 License

This repository currently has **no license**. To make the project open source, add a `LICENSE` file (e.g., MIT) and update this section accordingly.

---

## 🙌 Credits

- Built with Android, Firebase, and Material Design.
- Project name & code: AcadLink.
