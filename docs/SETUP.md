# Local setup — reproduce OmniMesh

Follow these steps on macOS, Linux, or Windows (WSL recommended for Make). Total time ~15 minutes once SDKs are installed.

## Prerequisites

| Tool | Version / notes |
|------|-----------------|
| **Node.js** | Match **`.nvmrc`** (currently **22**). Use [nvm](https://github.com/nvm-sh/nvm): `nvm install && nvm use` |
| **npm** | ≥ 9 |
| **JDK** | **17** (Android Gradle Plugin requirement) |
| **Android Studio** | Latest stable; install **SDK Platform 34**, build-tools, accept licenses |
| **Firebase account** | Free tier is enough for demo |
| **Physical Android phones** (recommended) | **Nearby Connections / BLE mesh demos do not work reliably across two emulators.** Use two real devices on the same account/build variant for P2P tests. |

Optional:

- **Firebase CLI** — `npm install -g firebase-tools` for deploy/emulators (`firebase login`).
- **Google Maps API key** — only if you want Maps JS instead of Leaflet on web (`REACT_APP_GOOGLE_MAPS_API_KEY`).

---

## One-command bootstrap

From the repository root:

```bash
make setup
```

This installs dependencies for **`web/`** and **`functions/`**. Then configure secrets (next sections).

Other useful targets:

```bash
make dev-web          # React app → http://localhost:3000
make build-web        # output in web/build (Firebase Hosting)
make android-debug    # ./gradlew :app:assembleDebug
make functions-emulators   # requires functions/.env + firebase-tools (see below)
make help
```

Equivalent without Make:

```bash
npm install --prefix web
npm install --prefix functions
```

### If GitHub emails “workflow failed” (Firebase Hosting)

1. Open **GitHub → your repo → Actions →** open the latest **red** run.
2. Read the failed step:
   - **Missing `FIREBASE_SERVICE_ACCOUNT_OMNIMESH_COMMAND`** — add **Settings → Secrets and variables → Actions** → New repository secret. Value = full JSON from **Firebase Console → Project settings → Service accounts → Generate new private key**. The service account needs permission to deploy Hosting (e.g. **Firebase Hosting Admin** or **Editor** on project `omnimesh-command`).
   - **`jq` invalid JSON** — you pasted markdown or truncated JSON; must be raw `{ ... }` key file contents only.
   - **`403` / `Permission denied` / `hosting:sites:list` fails** — In **Google Cloud Console → IAM**, that service account needs roles such as **Firebase Hosting Admin** (or **Editor** on the Firebase/GCP project). The JSON key’s **`project_id`** must match Firebase project **`omnimesh-command`** (or deploy will 403).
   - **`npm ci` / build errors** — open the log; fix lockfile or TypeScript/build issues locally first (`cd web && npm ci && npm run build`).
3. Optional: disable **Actions failure notifications** in your GitHub account notifications settings until secrets are fixed.

**Required GitHub Actions secrets** (names must match `.github/workflows/firebase-hosting-merge.yml`):

| Secret | Purpose |
| --- | --- |
| `FIREBASE_SERVICE_ACCOUNT_OMNIMESH_COMMAND` | Full service-account JSON for `firebase deploy` |
| `REACT_APP_FIREBASE_API_KEY` | Firebase web SDK (baked into `web/build` at CI time) |
| `REACT_APP_FIREBASE_AUTH_DOMAIN` | Firebase web SDK |
| `REACT_APP_FIREBASE_PROJECT_ID` | Firebase web SDK |
| `REACT_APP_FIREBASE_STORAGE_BUCKET` | Firebase web SDK |
| `REACT_APP_FIREBASE_MESSAGING_SENDER_ID` | Firebase web SDK |
| `REACT_APP_FIREBASE_APP_ID` | Firebase web SDK |
| `REACT_APP_GEMINI_API_KEY` | Optional — web Gemini client |
| `REACT_APP_GOOGLE_MAPS_API_KEY` | Optional — Maps on production web (Leaflet works without it) |

Local `web/.env` uses the same `REACT_APP_*` names. **Never commit** `.env` or paste keys into the repo.

### After re-cloning (new laptop / deleted folder)

GitHub Actions secrets stay on **GitHub** — they are **not** in the git clone. Your local keys live in **`secrets.properties`** (gitignored), which you must restore yourself.

1. **Clone the canonical repo** (always this URL — do not create a second repo):
   ```bash
   git clone git@github.com:Adya6714/OmniMesh.git
   cd OmniMesh
   ```
2. **Restore secrets locally** — copy your backed-up `secrets.properties` to the repo root, or:
   ```bash
   cp secrets.properties.example secrets.properties
   # fill in Firebase service-account JSON + firebaseConfig + GEMINI_API_KEY
   ```
3. **Sync to GitHub** (only needed once per machine, or if you created a new empty repo):
   ```bash
   gh auth login
   ./scripts/sync-github-actions-secrets.sh
   ```
   Verifies with `gh secret list -R Adya6714/OmniMesh` (expect 8 secrets).
4. **Local web dev** — optional `web/.env` with the same `REACT_APP_*` values (see `secrets.properties` firebase block).
5. **Deploy** — push to `main`, or **Actions → Deploy to Firebase Hosting on merge → Run workflow**.

**Do not** create a second GitHub repo when re-cloning; secrets are per-repo and will not copy over.

### Hosting parity (`localhost` vs `omnimesh-command.web.app`)

- **Same UI code:** Production hosting is exactly **`npm run build --prefix web`** output (`firebase.json` → `"public": "web/build"`). There is no separate “hosted” branch of React — only whatever was **last deployed**.
- **Redeploy after UI edits:** `npm run deploy:hosting` from repo root (needs `firebase login` once), or push to **`main`** so **Deploy to Firebase Hosting on merge** runs. You can also run that workflow manually (**Actions → Deploy to Firebase Hosting on merge → Run workflow**).
- **GitHub Actions secrets** must match `web/.env` names: especially **`REACT_APP_GOOGLE_MAPS_API_KEY`** (legacy alias **`REACT_APP_MAPS_API_KEY`** is still read by `FieldMap.jsx`).
- **CDN / browser cache:** `firebase.json` sets **`no-cache`** on `index.html` so new builds pick up fresh JS hashes; use a hard refresh if needed.
- **Android:** The handset UI is **Kotlin / Jetpack Compose** under `app/` — not the React bundle — but triage flows mirror the web demo conceptually.

---

## 1. Firebase project (web + Android)

1. Create a project in [Firebase Console](https://console.firebase.google.com/).
2. Add a **Web app** and copy the config object into **`web/.env`**:

   ```bash
   cp web/.env.example web/.env
   # Fill REACT_APP_FIREBASE_* values
   ```

3. Add an **Android app** with package **`omnimesh.command1`** (see `app/build.gradle.kts`).
4. Download **`google-services.json`** and place it at:

   ```
   app/google-services.json
   ```

   (`google-services.json` is **gitignored** — never commit it.)

5. Enable **Firestore** (native mode) and **Anonymous Authentication**.

6. *(Recommended)* Add **`firestore.rules`**: start from **`firestore.rules.example`** in this repo, tune for your demo, then deploy:

   ```bash
   firebase deploy --only firestore:rules
   ```

---

## 2. Android secrets (`secrets.properties`)

Gradle reads **`secrets.properties`** at the repo root (also **gitignored**):

```bash
cp secrets.properties.example secrets.properties
# Set GEMINI_API_KEY=... for VisionClassifier / DispatchAgent BuildConfig fields
```

Rebuild after changes:

```bash
./gradlew :app:assembleDebug
```

---

## 3. Cloud Functions (Gemini dispatch)

Functions read **`GEMINI_API_KEY`** from the environment (`functions/index.js`).

**Local emulator**

```bash
cp functions/.env.example functions/.env
# Put GEMINI_API_KEY in functions/.env

cd functions && npm install
npm run serve
# or: npx firebase-tools emulators:start --only functions
```

**Production**

Do **not** store API keys in `firebase.json`. Use [Firebase secrets](https://firebase.google.com/docs/functions/config-env) for `GEMINI_API_KEY`, or configure via Google Cloud Console for the Functions service account — follow current Firebase v2 docs.

---

## 4. Run the web dashboard

```bash
make dev-web
```

Open **Victim / Responder / Command** modes from the shell UI. Toggle **Demo** to use seeded packets without live Firestore.

If Firestore env vars are missing, the app still runs in demo-oriented modes but live sync will be disabled (see browser console warnings).

---

## 5. Run the Android app

1. Open the **`OmniMesh`** folder in **Android Studio**.
2. Select a device (**physical device strongly recommended** for mesh/Nearby).
3. **Run** the `app` configuration.

**USB debugging**

- Enable **Developer options** → **USB debugging** on the phone.
- Accept the RSA fingerprint dialog when plugging in.

**Same-network / mesh**

- Grant **Nearby**, **Location**, and **Bluetooth** permissions when prompted.
- Two handsets with the same build signing as configured for Nearby (Google Play Services required).

---

## 6. End-to-end checklist

- [ ] `make setup` succeeds  
- [ ] `web/.env` populated; `make dev-web` loads UI  
- [ ] `app/google-services.json` present; app installs and opens  
- [ ] `secrets.properties` has `GEMINI_API_KEY` if testing vision/dispatch on device  
- [ ] Firestore rules deployed; Anonymous auth enabled  
- [ ] *(Optional)* Functions deployed or emulator running for dispatch analysis writes  

---

## Security note

If an API key was ever committed to git history, **rotate it** in [Google AI Studio](https://aistudio.google.com/) / Cloud Console and update local `.env` / `secrets.properties` only — never recommit keys.

---

## What we removed / ignore for a clean submission tree

- Duplicate **`ui-upload/`** snapshots (canonical sources live under **`app/`** and **`web/src`**).
- Stray root **`OmniMeshApp.kt`** / empty **`AndroidManifest.xml`** (real manifests live under **`app/src`**).
- IDE caches, `.kotlin/` error logs, large ML training audio blobs — see **`.gitignore`**.

Training notebooks under **`training/`** are optional for running the shipped app; large binaries can stay untracked.
