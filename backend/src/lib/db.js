import crypto from "node:crypto";
import { readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const DB_PATH = path.resolve(__dirname, "../../data/seed.json");

function isHashedPassword(value) {
  return typeof value === "string" && /^[a-f0-9]{64}$/i.test(value);
}

export function hashPassword(password) {
  return crypto.createHash("sha256").update(password).digest("hex");
}

export async function loadDb() {
  const raw = await readFile(DB_PATH, "utf8");
  const db = JSON.parse(raw);
  let changed = false;

  db.users = db.users.map((user) => {
    if (isHashedPassword(user.password)) {
      return user;
    }

    changed = true;
    return {
      ...user,
      password: hashPassword(user.password),
    };
  });

  if (changed) {
    await saveDb(db);
  }

  return db;
}

export async function saveDb(db) {
  await writeFile(DB_PATH, `${JSON.stringify(db, null, 2)}\n`, "utf8");
}

export function nextId(prefix) {
  return `${prefix}-${crypto.randomUUID().slice(0, 8)}`;
}

export function appendLog(db, payload) {
  db.logs.unshift({
    id: nextId("log"),
    createdAt: new Date().toISOString(),
    ...payload,
  });
}

export function createMessage(db, payload) {
  db.messages.unshift({
    id: nextId("message"),
    read: false,
    createdAt: new Date().toISOString(),
    ...payload,
  });
}
