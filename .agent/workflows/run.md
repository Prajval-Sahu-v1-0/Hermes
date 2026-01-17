---
description: how to run the application
---

To run the Hermes project and test the new search functionality, follow these steps:

### 1. Start the Backend (Spring Boot)
1. Open a terminal in the `d:\Documents\Hermes\backend` directory.
2. Run the following command to start the Maven development server:
   ```bash
   mvn spring-boot:run
   ```
3. Wait for the terminal to show `Started HermesApplication`. The backend is now running at `http://localhost:8080`.

> [!NOTE]
> Make sure you have **Java 21** installed and configured in your environment.

### 2. Start the Frontend
Since the frontend uses plain HTML/JS/CSS, you have two options:

**Option A: Using a local server (Recommended)**
If you have Node.js installed, run this from the `d:\Documents\Hermes` root:
```bash
npx serve ./
```
Then open the provided local URL (usually `http://localhost:3000`).

**Option B: Open the file directly**
1. Navigate to `d:\Documents\Hermes` in your file explorer.
2. Right-click `index.html` and select **Open with...** -> **Google Chrome** (or your preferred browser).

### 3. Verify the Search
Once both are active:
1. Click the **center icon** to open the search bar.
2. Type a genre like `anime edits`.
3. You should see the **Generated Research Queries** (Phase One) and the **Creator Profiles** (Phase Two) appear on the page.
