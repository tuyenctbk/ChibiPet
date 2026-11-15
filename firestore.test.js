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

function validPetPayload(uid) {
  const pastDate = new Date(Date.now() - 1000);
  return {
    userId: uid,
    petName: "Mochi",
    speciesColor: "mint",
    equippedOutfit: "none",
    unlockedOutfits: ["none", "bow"],
    level: 1,
    xp: 0,
    coins: 50,
    happiness: 85,
    energy: 90,
    totalCompleted: 0,
    streakFreezes: 1,
    createdAt: pastDate,
    updatedAt: pastDate,
  };
}

function validHabitPayload(uid) {
  const pastDate = new Date(Date.now() - 1000);
  return {
    userId: uid,
    title: "Drink Water",
    category: "hydration",
    xpReward: 25,
    streakCount: 0,
    lastVerifiedDate: "",
    lastVerificationNote: "",
    createdAt: pastDate,
    updatedAt: pastDate,
  };
}

function validPetCardPayload(uid, code) {
  return {
    userId: uid,
    buddyCode: code,
    petName: "Mochi",
    speciesColor: "mint",
    equippedOutfit: "bow",
    level: 2,
    totalCompleted: 5,
    topStreak: 3,
    updatedAt: new Date(Date.now() - 1000),
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

test("Unauthenticated user cannot read or write habits or petProfile", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/habits`).get());
  await assertFails(unauthDb.doc(`users/${ALICE_UID}/petProfile/main`).get());
  await assertFails(unauthDb.doc(`users/${ALICE_UID}/habits/h1`).set(validHabitPayload(ALICE_UID)));
  await assertFails(unauthDb.doc(`petCards/PET-ALICE1`).get());
});

test("Owner can create, read, and update their PetProfile including streakFreezes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.doc(`users/${ALICE_UID}/petProfile/main`);
  await assertSucceeds(ref.set(validPetPayload(ALICE_UID)));
  await assertSucceeds(ref.get());
  await assertSucceeds(ref.update({
    xp: 30,
    coins: 75,
    streakFreezes: 2,
    lastDailyBonusDate: "2026-11-14",
    equippedOutfit: "bow",
    updatedAt: new Date(Date.now() - 500),
  }));
});

test("Cross-user access to PetProfile, Habits, and Friends is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/petProfile/main`).set(validPetPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/habits/h1`).set(validHabitPayload(ALICE_UID)));

  await assertFails(bobDb.doc(`users/${ALICE_UID}/petProfile/main`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/habits`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/habits/h1`).delete());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/friends`).get());
});

test("Shadow update with ghost field or forged userId is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/petProfile/main`).set({
      ...validPetPayload(ALICE_UID),
      isAdmin: true,
    })
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/habits/h1`).set(validHabitPayload(BOB_UID))
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/petProfile/main`).set({
      ...validPetPayload(ALICE_UID),
      streakFreezes: 99,
    })
  );
});

test("Invalid enum or oversized string is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/habits/h1`).set({
      ...validHabitPayload(ALICE_UID),
      category: "invalid_cat",
    })
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/petProfile/main`).set({
      ...validPetPayload(ALICE_UID),
      petName: "A".repeat(40),
    })
  );
});

test("Chat messages can be created and listed by owner, updates rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const msgRef = aliceDb.doc(`users/${ALICE_UID}/chatMessages/m1`);
  await assertSucceeds(msgRef.set({
    userId: ALICE_UID,
    sender: "user",
    text: "Hi Mochi!",
    createdAt: new Date(Date.now() - 1000),
  }));
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/chatMessages`).get());
  await assertFails(msgRef.update({ text: "Edited" }));
});

test("Buddy Cards allow get by code, block collection listing, and support friend cheers", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const aliceCardRef = aliceDb.doc("petCards/PET-ALICE1");
  await assertSucceeds(aliceCardRef.set(validPetCardPayload(ALICE_UID, "PET-ALICE1")));

  // Listing all petCards is forbidden
  await assertFails(bobDb.collection("petCards").get());

  // Looking up by exact code succeeds
  await assertSucceeds(bobDb.doc("petCards/PET-ALICE1").get());

  // Bob cannot overwrite Alice's card
  await assertFails(bobDb.doc("petCards/PET-ALICE1").update({ level: 99, updatedAt: new Date(Date.now() - 500) }));

  // Bob can send a cheering boop to Alice's card
  const cheerRef = bobDb.doc("petCards/PET-ALICE1/cheers/c1");
  await assertSucceeds(cheerRef.set({
    fromUserId: BOB_UID,
    toUserId: ALICE_UID,
    fromPetName: "Boba",
    message: "Keep crushing your water habit!",
    claimed: false,
    createdAt: new Date(Date.now() - 1000),
  }));

  // Bob cannot list Alice's cheers
  await assertFails(bobDb.collection("petCards/PET-ALICE1/cheers").where("toUserId", "==", ALICE_UID).get());

  // Alice can query her own cheers and claim them
  await assertSucceeds(aliceDb.collection("petCards/PET-ALICE1/cheers").where("toUserId", "==", ALICE_UID).get());
  await assertSucceeds(aliceDb.doc("petCards/PET-ALICE1/cheers/c1").update({ claimed: true }));
});

test("Owner can create, update, and reset UsageQuota while cross-user and invalid payloads fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const pastDate = new Date(Date.now() - 1000);

  const quotaRef = aliceDb.doc(`users/${ALICE_UID}/usageQuota/daily`);
  const validQuota = {
    userId: ALICE_UID,
    dateIso: "2026-11-14",
    visionUsed: 2,
    chatUsed: 5,
    studioUsed: 1,
    bonusGraceCredits: 0,
    graceClaimed: false,
    ecoLiteMode: false,
    fallbackCascadeCount: 0,
    createdAt: pastDate,
    updatedAt: pastDate,
  };

  await assertSucceeds(quotaRef.set(validQuota));
  await assertSucceeds(quotaRef.get());

  // Increment usage & claim grace credits
  await assertSucceeds(
    quotaRef.update({
      visionUsed: 3,
      bonusGraceCredits: 3,
      graceClaimed: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Daily rollover reset
  await assertSucceeds(
    quotaRef.update({
      dateIso: "2026-11-15",
      visionUsed: 0,
      chatUsed: 0,
      studioUsed: 0,
      bonusGraceCredits: 0,
      graceClaimed: false,
      fallbackCascadeCount: 0,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Out-of-bounds bonusGraceCredits or cross-user read/write must fail
  await assertFails(
    quotaRef.update({
      bonusGraceCredits: 999,
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(bobDb.doc(`users/${ALICE_UID}/usageQuota/daily`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/usageQuota/daily`).delete());
});

