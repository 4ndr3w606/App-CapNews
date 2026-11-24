const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const { promisify } = require("node:util");
const express = require("express");
const envPath = path.join(__dirname, ".env");
if (fs.existsSync(envPath)) process.loadEnvFile(envPath);
const Database = require("better-sqlite3");
const { rateLimit } = require("express-rate-limit");

const app = express();
const port = Number(process.env.PORT || 3000);
const newsOnly = process.env.NEWS_ONLY === "true";
const gnewsApiKey = process.env.GNEWS_API_KEY?.trim();
if (newsOnly && !gnewsApiKey) {
  throw new Error("Configura GNEWS_API_KEY para iniciar el servicio de noticias.");
}
const databasePath = newsOnly ? ":memory:" : path.resolve(
  process.env.AUTH_DB_PATH || path.join(__dirname, "data", "capnews.sqlite")
);
const scrypt = promisify(crypto.scrypt);
const SESSION_LIFETIME_MS = 7 * 24 * 60 * 60 * 1000;
const REGISTRATION_TERMS_VERSION = 1;
const SCRYPT_OPTIONS = { N: 32_768, r: 8, p: 1, maxmem: 64 * 1024 * 1024 };

const trustProxyHops = Number(process.env.TRUST_PROXY_HOPS || 0);
if (!Number.isInteger(trustProxyHops) || trustProxyHops < 0) {
  throw new Error("TRUST_PROXY_HOPS debe ser un entero igual o mayor que cero.");
}
if (trustProxyHops > 0) app.set("trust proxy", trustProxyHops);

if (!newsOnly) fs.mkdirSync(path.dirname(databasePath), { recursive: true });
const db = new Database(databasePath);
db.pragma("journal_mode = WAL");
db.pragma("foreign_keys = ON");
db.exec(`
  CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    email TEXT NOT NULL COLLATE NOCASE UNIQUE,
    name TEXT,
    password_hash TEXT NOT NULL,
    created_at INTEGER NOT NULL
  );
  CREATE TABLE IF NOT EXISTS sessions (
    token_hash TEXT PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at INTEGER NOT NULL
  );
  CREATE INDEX IF NOT EXISTS sessions_user_id_idx ON sessions(user_id);
  CREATE INDEX IF NOT EXISTS sessions_expires_at_idx ON sessions(expires_at);
`);
const userColumns = new Set(db.pragma("table_info(users)").map((column) => column.name));
if (!userColumns.has("name")) {
  db.exec("ALTER TABLE users ADD COLUMN name TEXT");
}
if (!userColumns.has("terms_version")) {
  db.exec("ALTER TABLE users ADD COLUMN terms_version INTEGER");
}
if (!userColumns.has("terms_accepted_at")) {
  db.exec("ALTER TABLE users ADD COLUMN terms_accepted_at INTEGER");
}

app.disable("x-powered-by");
app.use((request, response, next) => {
  response.set("X-Content-Type-Options", "nosniff");
  response.set("Cache-Control", "no-store");
  next();
});
app.use(express.json({ limit: "8kb", strict: true }));

const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 10,
  standardHeaders: "draft-8",
  legacyHeaders: false,
  message: {
    error: { code: "RATE_LIMITED", message: "Demasiados intentos. Espera y vuelve a probar." },
  },
});

function sendError(response, status, code, message) {
  return response.status(status).json({ error: { code, message } });
}

function normalizeEmail(value) {
  return typeof value === "string" ? value.trim().toLowerCase() : "";
}

function isValidEmail(email) {
  return email.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

async function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const derivedKey = await scrypt(password, salt, 64, SCRYPT_OPTIONS);
  return `scrypt$${salt.toString("base64url")}$${derivedKey.toString("base64url")}`;
}

async function verifyPassword(password, storedHash) {
  const [algorithm, saltText, hashText] = storedHash.split("$");
  if (algorithm !== "scrypt" || !saltText || !hashText) return false;

  const salt = Buffer.from(saltText, "base64url");
  const expected = Buffer.from(hashText, "base64url");
  if (salt.length !== 16 || expected.length !== 64) return false;

  const actual = await scrypt(password, salt, expected.length, SCRYPT_OPTIONS);
  return crypto.timingSafeEqual(actual, expected);
}

const dummyPasswordHash = newsOnly ? null : hashPassword(crypto.randomBytes(32).toString("hex"));

function tokenDigest(token) {
  return crypto.createHash("sha256").update(token).digest("hex");
}

function createSession(userId) {
  const token = crypto.randomBytes(32).toString("base64url");
  const expiresAt = Date.now() + SESSION_LIFETIME_MS;
  db.prepare("INSERT INTO sessions (token_hash, user_id, expires_at) VALUES (?, ?, ?)")
    .run(tokenDigest(token), userId, expiresAt);
  return { token, expiresAt };
}

function getBearerToken(request) {
  const match = /^Bearer ([A-Za-z0-9_-]{40,})$/.exec(request.get("authorization") || "");
  return match?.[1] || null;
}

function requireSession(request, response, next) {
  const token = getBearerToken(request);
  if (!token) return sendError(response, 401, "UNAUTHENTICATED", "Inicia sesión para continuar.");

  const now = Date.now();
  db.prepare("DELETE FROM sessions WHERE expires_at <= ?").run(now);
  const session = db.prepare(`
    SELECT users.id, users.email, users.name, sessions.expires_at AS expiresAt
    FROM sessions JOIN users ON users.id = sessions.user_id
    WHERE sessions.token_hash = ? AND sessions.expires_at > ?
  `).get(tokenDigest(token), now);

  if (!session) return sendError(response, 401, "UNAUTHENTICATED", "La sesión venció. Inicia sesión de nuevo.");
  const { expiresAt, ...user } = session;
  request.auth = { token, user, expiresAt };
  return next();
}

app.get("/health", (_request, response) => response.json({ status: "ok" }));
if (newsOnly) {
  app.use(["/api/auth", "/api/news"], (_request, response) =>
    sendError(response, 404, "NOT_FOUND", "Ruta no disponible."));
}

app.post("/api/auth/register", authLimiter, async (request, response, next) => {
  const email = normalizeEmail(request.body?.email);
  const password = request.body?.password;
  const rawName = request.body?.name;
  const name = typeof rawName === "string" ? rawName.trim() : null;

  if (request.body?.termsVersion !== REGISTRATION_TERMS_VERSION) {
    return sendError(response, 400, "TERMS_REQUIRED", "Acepta la versión vigente de los términos y condiciones.");
  }

  if (!name || name.length > 120) {
    return sendError(response, 400, "INVALID_NAME", "Ingresa un nombre de hasta 120 caracteres.");
  }

  if (!isValidEmail(email) || typeof password !== "string" || password.length < 8 || password.length > 128) {
    return sendError(response, 400, "INVALID_INPUT", "Ingresa un correo válido y una contraseña de 8 a 128 caracteres.");
  }

  try {
    const passwordHash = await hashPassword(password);
    const registration = db.transaction(() => {
      const now = Date.now();
      const result = db.prepare(`
        INSERT INTO users (email, name, password_hash, created_at, terms_version, terms_accepted_at)
        VALUES (?, ?, ?, ?, ?, ?)
      `).run(email, name, passwordHash, now, REGISTRATION_TERMS_VERSION, now);
      const user = { id: Number(result.lastInsertRowid), email, name };
      return { user, ...createSession(user.id) };
    })();
    return response.status(201).json(registration);
  } catch (error) {
    if (error.code === "SQLITE_CONSTRAINT_UNIQUE") {
      return sendError(response, 409, "REGISTRATION_FAILED", "No se pudo crear la cuenta con esos datos.");
    }
    return next(error);
  }
});

app.post("/api/auth/login", authLimiter, async (request, response, next) => {
  const email = normalizeEmail(request.body?.email);
  const password = request.body?.password;
  if (!isValidEmail(email) || typeof password !== "string" || password.length < 8 || password.length > 128) {
    return sendError(response, 401, "INVALID_CREDENTIALS", "Correo o contraseña incorrectos.");
  }

  try {
    const account = db.prepare("SELECT id, email, name, password_hash FROM users WHERE email = ?").get(email);
    const passwordMatches = account
      ? await verifyPassword(password, account.password_hash)
      : await verifyPassword(password, await dummyPasswordHash);
    if (!account || !passwordMatches) {
      return sendError(response, 401, "INVALID_CREDENTIALS", "Correo o contraseña incorrectos.");
    }

    const session = createSession(account.id);
    return response.json({
      user: { id: account.id, email: account.email, name: account.name },
      token: session.token,
      expiresAt: session.expiresAt,
    });
  } catch (error) {
    return next(error);
  }
});

app.get("/api/auth/me", requireSession, (request, response) => {
  return response.json({ user: request.auth.user, expiresAt: request.auth.expiresAt });
});

app.post("/api/auth/logout", requireSession, (request, response) => {
  db.prepare("DELETE FROM sessions WHERE token_hash = ?").run(tokenDigest(request.auth.token));
  return response.status(204).end();
});

const newsLimiter = rateLimit({
  windowMs: 60 * 1000,
  limit: 30,
  keyGenerator: (request) => String(request.auth.user.id),
  standardHeaders: "draft-8",
  legacyHeaders: false,
  message: { error: { code: "RATE_LIMITED", message: "Espera un momento para consultar noticias." } },
});

const publicNewsLimiter = rateLimit({ windowMs: 60 * 1000, limit: 30 });
const publicNewsBudget = rateLimit({
  windowMs: 24 * 60 * 60 * 1000,
  limit: Number(process.env.PUBLIC_NEWS_DAILY_LIMIT ?? 100),
  keyGenerator: () => "public-news",
});

app.get("/api/news/:endpoint", requireSession, newsLimiter, serveNews);
if (newsOnly || process.env.PUBLIC_NEWS_ENABLED === "true") {
  app.get("/api/public/news/:endpoint", publicNewsLimiter, publicNewsBudget, serveNews);
}

async function serveNews(request, response) {
  const endpoint = request.params.endpoint;
  if (!["search", "top-headlines"].includes(endpoint)) {
    return sendError(response, 404, "NOT_FOUND", "Consulta no disponible.");
  }
  if (!gnewsApiKey) return sendError(response, 503, "NEWS_NOT_CONFIGURED", "El servicio de noticias está pendiente de configuración.");

  const url = new URL(endpoint, "https://gnews.io/api/v4/");
  const allowed = endpoint === "search"
    ? ["q", "lang", "country", "sortby", "in"]
    : ["category", "lang"];
  for (const key of Object.keys(request.query)) {
    const value = request.query[key];
    if (!allowed.includes(key) || typeof value !== "string" || !value.trim() || value.length > 200) {
      return sendError(response, 400, "INVALID_QUERY", "Los parámetros de noticias no son válidos.");
    }
    url.searchParams.set(key, value.trim());
  }
  if (endpoint === "search" && !url.searchParams.has("q")) {
    return sendError(response, 400, "INVALID_QUERY", "Ingresa un término de búsqueda.");
  }
  if (endpoint === "search") url.searchParams.set("in", "title,description,content");
  try {
    const upstream = await fetch(url, {
      headers: { "X-Api-Key": gnewsApiKey },
      signal: AbortSignal.timeout(10_000),
    });
    if (!upstream.ok) {
      const status = upstream.status === 429 ? 429 : 502;
      return sendError(response, status, "NEWS_PROVIDER_ERROR", "El proveedor de noticias no está disponible. Inténtalo más tarde.");
    }
    const data = await upstream.json();
    if (!Array.isArray(data.articles)) throw new Error("Invalid provider response");
    return response.json({ totalArticles: data.totalArticles, articles: data.articles });
  } catch {
    return sendError(response, 502, "NEWS_UNAVAILABLE", "No se pudo consultar el proveedor de noticias.");
  }
}

app.use((error, _request, response, _next) => {
  if (error.type === "entity.parse.failed" || error.type === "entity.too.large") {
    return sendError(response, 400, "INVALID_REQUEST", "La solicitud no es válida.");
  }
  console.error("Error interno al procesar una solicitud de autenticación.");
  return sendError(response, 500, "INTERNAL_ERROR", "Ocurrió un error interno.");
});

const server = app.listen(port, "0.0.0.0", () => {
  console.log(`API de CapNews escuchando en el puerto ${port}.`);
});

function shutdown() {
  server.close(() => {
    db.close();
    process.exit(0);
  });
}
process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
