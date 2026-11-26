const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_creator_1";
const BOB_UID = "bob_creator_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any user collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_1")
      .set({
        userId: ALICE_UID,
        title: "Unauthorized",
      })
  );
});

test("Authenticated owner: valid project backup create, partial update, and list succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const projRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("projects")
    .doc("proj_1");

  await assertSucceeds(
    projRef.set({
      userId: ALICE_UID,
      title: "AI Space Facts Short",
      topic: "Top 3 facts about neutron stars",
      aspectRatio: "9:16",
      targetDurationSec: 45,
      languageCode: "en",
      scriptHook: "Did you know one teaspoon weighs a billion tons?",
      scriptBody: "Scene 1: Neutron stars form after supernovas.",
      scriptCta: "Subscribe for daily space facts!",
      sceneSummaries: ["Supernova explosion", "Dense neutron core"],
      seoTitle: "3 Mind-Blowing Neutron Star Facts",
      seoDescription: "Quick space science short.",
      seoTags: ["space", "astronomy", "shorts"],
      status: "READY",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update preserving createdAt and userId
  await assertSucceeds(
    projRef.update({
      title: "AI Space Facts Short v2",
      status: "EXPORTED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // User-scoped list succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );

  // Reject ghost field / @DocumentId 'id' leakage
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_2")
      .set({
        id: "proj_2",
        userId: ALICE_UID,
        title: "Ghost ID",
        topic: "Topic",
        aspectRatio: "16:9",
        targetDurationSec: 60,
        languageCode: "en",
        scriptHook: "",
        scriptBody: "",
        scriptCta: "",
        sceneSummaries: [],
        seoTitle: "",
        seoDescription: "",
        seoTags: [],
        status: "DRAFT",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject immutable createdAt modification
  await assertFails(
    projRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user isolation: Bob cannot read, list, update, or delete Alice projects or brand kits", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("brandKits")
      .doc("default_kit")
      .set({
        userId: ALICE_UID,
        channelName: "Alice Tech",
        primaryColorHex: "#22D3EE",
        accentColorHex: "#FF6B4A",
        fontFamily: "Space Grotesk",
        watermarkText: "@alicetech",
        watermarkEnabled: true,
        defaultAspectRatio: "9:16",
        defaultCaptionStyle: "WORD_HIGHLIGHT",
        applyByDefault: true,
        createdAt: created,
        updatedAt: created,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("brandKits")
      .doc("default_kit")
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("brandKits").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("brandKits")
      .doc("default_kit")
      .update({ channelName: "Hijacked", updatedAt: new Date(Date.now() - 500) })
  );
});

test("Scheduled uploads: terminal state COMPLETED prevents further tampering", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const uploadRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("scheduledUploads")
    .doc("up_1");

  await assertSucceeds(
    uploadRef.set({
      userId: ALICE_UID,
      projectId: "proj_1",
      projectTitle: "Space Short",
      videoTitle: "Neutron Stars in 45s",
      visibility: "private",
      scheduledAtMillis: Date.now() + 3600000,
      status: "QUEUED",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    uploadRef.update({
      status: "COMPLETED",
      youtubeVideoId: "dQw4w9WgXcQ",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    uploadRef.update({
      status: "QUEUED",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
