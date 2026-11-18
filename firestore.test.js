const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validInstancePayload(uid, offsetMs = 10000) {
  const ts = new Date(Date.now() - offsetMs);
  return {
    userId: uid,
    name: "AFK RPG Node 01",
    profileMode: "GAME_AFK",
    resolutionPreset: "FHD_1080",
    userAgentMode: "ANDROID_MOBILE",
    targetUrl: "https://play.example.org",
    status: "STOPPED",
    assignedMacroId: "",
    keepAwake: true,
    adShieldEnabled: true,
    httpsOnly: true,
    totalRunSeconds: 0,
    cycleCount: 0,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validMacroPayload(uid, offsetMs = 10000) {
  const ts = new Date(Date.now() - offsetMs);
  return {
    userId: uid,
    name: "Auto Farm Loop",
    description: "Taps collect button and waits 3 seconds",
    loopForever: true,
    maxLoops: 100,
    intervalMs: 3000,
    steps: ["TAP:0.50,0.70:Tap Collect", "WAIT:3000:Wait 3s"],
    runCount: 0,
    createdAt: ts,
    updatedAt: ts,
  };
}

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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/instances`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/instances`).doc("inst_1").set(validInstancePayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/macros`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/bookmarks`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/downloads`).get());
});

test("Owner can create, list, partially update, and delete VirtualInstance", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection(`users/${ALICE_UID}/instances`).doc("inst_01");

  await assertSucceeds(ref.set(validInstancePayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/instances`).get());

  await assertSucceeds(
    ref.update({
      status: "AFK_BACKGROUND",
      totalRunSeconds: 120,
      cycleCount: 15,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    ref.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field / @DocumentId 'id' injection on fresh create
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/instances`).doc("inst_02").set({
      ...validInstancePayload(ALICE_UID),
      id: "inst_02",
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/instances`).doc("inst_03").set({
      ...validInstancePayload(ALICE_UID),
      createdAt: new Date(Date.now() + 60000),
    })
  );

  await assertSucceeds(ref.delete());
});

test("Cross-user access to instances and macros is strictly denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection(`users/${BOB_UID}/instances`).doc("bob_inst").set(validInstancePayload(BOB_UID));
    await ctx.firestore().collection(`users/${BOB_UID}/macros`).doc("bob_macro").set(validMacroPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection(`users/${BOB_UID}/instances`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/instances`).doc("bob_inst").get());
  await assertFails(
    aliceDb.collection(`users/${BOB_UID}/instances`).doc("bob_inst").update({
      name: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(aliceDb.collection(`users/${BOB_UID}/macros`).doc("bob_macro").delete());
});

test("Owner can create, update, and validate bounds on AfkMacro, SecureBookmark, and DownloadRecord", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const macroRef = aliceDb.collection(`users/${ALICE_UID}/macros`).doc("macro_1");
  await assertSucceeds(macroRef.set(validMacroPayload(ALICE_UID)));
  await assertSucceeds(
    macroRef.update({
      runCount: 4,
      steps: ["TAP:0.50,0.50:Tap", "WAIT:1000:Wait", "RELOAD:0:Refresh"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject oversized steps (> 30)
  await assertFails(
    macroRef.update({
      steps: Array.from({ length: 31 }, (_, i) => `WAIT:500:Step ${i}`),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  const bmRef = aliceDb.collection(`users/${ALICE_UID}/bookmarks`).doc("bm_1");
  await assertSucceeds(
    bmRef.set({
      userId: ALICE_UID,
      title: "Idle Portal",
      url: "https://example.org",
      category: "GAMING",
      createdAt: new Date(Date.now() - 10000),
      updatedAt: new Date(Date.now() - 10000),
    })
  );
  await assertFails(
    bmRef.update({
      category: "INVALID_CAT",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  const dlRef = aliceDb.collection(`users/${ALICE_UID}/downloads`).doc("dl_1");
  await assertSucceeds(
    dlRef.set({
      userId: ALICE_UID,
      fileName: "patch_notes.json",
      url: "https://example.org/patch_notes.json",
      mimeType: "application/json",
      sizeBytes: 2048,
      status: "QUEUED",
      localUri: "",
      createdAt: new Date(Date.now() - 10000),
      updatedAt: new Date(Date.now() - 10000),
    })
  );
  await assertSucceeds(
    dlRef.update({
      status: "COMPLETED",
      sizeBytes: 4096,
      localUri: "content://downloads/public_downloads/1",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
