import { getApp, getApps, initializeApp } from "firebase/app";
import { getFirestore } from "firebase/firestore";
import { getAuth, signInAnonymously } from "firebase/auth";

const firebaseConfig = {
  apiKey: process.env.REACT_APP_FIREBASE_API_KEY,
  authDomain: process.env.REACT_APP_FIREBASE_AUTH_DOMAIN,
  projectId: process.env.REACT_APP_FIREBASE_PROJECT_ID,
  storageBucket: process.env.REACT_APP_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: process.env.REACT_APP_FIREBASE_MESSAGING_SENDER_ID,
  appId: process.env.REACT_APP_FIREBASE_APP_ID,
};

const requiredKeys = ["apiKey", "authDomain", "projectId", "appId"];

let db = null;
let authReady = Promise.resolve();

try {
  const missing = requiredKeys.filter((key) => !firebaseConfig[key]);
  if (missing.length > 0) {
    console.warn(
      `[OmniMesh] Missing Firebase env at build time (${missing.join(", ")}). ` +
        "Create web/.env or inject REACT_APP_FIREBASE_* in CI, then rebuild."
    );
  } else {
    const app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApp();
    db = getFirestore(app);
    const auth = getAuth(app);
    authReady = signInAnonymously(auth)
      .then(() => console.log("[FirebaseAuth] Signed in anonymously"))
      .catch((err) => console.warn("[FirebaseAuth] Anonymous sign-in failed:", err.message));
    console.log("[FirebaseFirestore] Initialized web app for project", firebaseConfig.projectId);
  }
} catch (err) {
  console.error("[OmniMesh] Firebase initialization failed:", err);
}

export const isFirebaseConfigured = Boolean(db);
export { db, authReady };
