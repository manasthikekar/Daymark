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

test("Unauthenticated user cannot list, get, create, update, or delete events", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("events").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("events")
      .doc("evt_1")
      .set({
        userId: ALICE_UID,
        title: "Unauthorized Event",
        dateEpochDay: 20415,
        startMinute: 540,
        endMinute: 600,
        allDay: false,
        category: "WORK",
        reminderMinutes: 10,
        createdAt: new Date(Date.now() - 1000),
        updatedAt: new Date(Date.now() - 1000),
      })
  );
});

test("Authenticated owner: valid Kotlin create, partial update, list, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const eventsCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("events");

  // Empty collection list succeeds
  await assertSucceeds(eventsCol.get());

  // Mirror EventRepository create payload (no @DocumentId `id` field, optional notes/location omitted when null)
  await assertSucceeds(
    eventsCol.doc("evt_1").set({
      userId: ALICE_UID,
      title: "Design Sprint Review",
      location: "Studio B",
      dateEpochDay: 20415,
      startMinute: 540,
      endMinute: 630,
      allDay: false,
      category: "WORK",
      reminderMinutes: 10,
      createdAt: created,
      updatedAt: created,
    })
  );

  // List populated collection succeeds
  await assertSucceeds(eventsCol.get());

  // Mirror partial update payload preserving userId and createdAt
  await assertSucceeds(
    eventsCol.doc("evt_1").update({
      title: "Design Sprint Retrospective",
      notes: "Bring Q4 roadmap",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Delete succeeds
  await assertSucceeds(eventsCol.doc("evt_1").delete());
});

test("Security bounds reject invalid payloads, shadow fields, immutable changes, and cross-user access", async () => {
  const created = new Date(Date.now() - 10000);

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("events")
      .doc("bob_evt")
      .set({
        userId: BOB_UID,
        title: "Bob Private Meeting",
        dateEpochDay: 20415,
        startMinute: 600,
        endMinute: 660,
        allDay: false,
        category: "PERSONAL",
        reminderMinutes: -1,
        createdAt: created,
        updatedAt: created,
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const aliceEvents = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("events");
  const bobEvents = aliceDb
    .collection("users")
    .doc(BOB_UID)
    .collection("events");

  // Cross-user isolation
  await assertFails(bobEvents.get());
  await assertFails(bobEvents.doc("bob_evt").get());
  await assertFails(
    bobEvents.doc("bob_evt").update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobEvents.doc("bob_evt").delete());

  // Reject `@DocumentId` `id` field inside document body
  await assertFails(
    aliceEvents.doc("evt_bad_id").set({
      id: "evt_bad_id",
      userId: ALICE_UID,
      title: "Bad ID Field",
      dateEpochDay: 20415,
      startMinute: 540,
      endMinute: 600,
      allDay: false,
      category: "WORK",
      reminderMinutes: -1,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject invalid category & endMinute < startMinute & future timestamp
  await assertFails(
    aliceEvents.doc("evt_bad_cat").set({
      userId: ALICE_UID,
      title: "Bad Category",
      dateEpochDay: 20415,
      startMinute: 540,
      endMinute: 600,
      allDay: false,
      category: "UNKNOWN",
      reminderMinutes: -1,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(
    aliceEvents.doc("evt_bad_time").set({
      userId: ALICE_UID,
      title: "Inverted Time",
      dateEpochDay: 20415,
      startMinute: 700,
      endMinute: 600,
      allDay: false,
      category: "FOCUS",
      reminderMinutes: -1,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(
    aliceEvents.doc("evt_future_ts").set({
      userId: ALICE_UID,
      title: "Future Timestamp",
      dateEpochDay: 20415,
      startMinute: 540,
      endMinute: 600,
      allDay: false,
      category: "FOCUS",
      reminderMinutes: -1,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: created,
    })
  );

  // Valid create, then reject immutable createdAt mutation
  await assertSucceeds(
    aliceEvents.doc("evt_valid").set({
      userId: ALICE_UID,
      title: "Morning Run",
      dateEpochDay: 20415,
      startMinute: 420,
      endMinute: 480,
      allDay: false,
      category: "HEALTH",
      reminderMinutes: 30,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(
    aliceEvents.doc("evt_valid").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
