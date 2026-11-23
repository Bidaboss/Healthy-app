// Dépose l'APK sur le dossier Drive des mises à jour (docs/architecture.md, « Publication automatique »).
// Lancé par le workflow Android sur main, avec la clé d'un compte de service Google.
// Un compte de service n'a pas d'espace Drive : il ne peut pas créer de fichier, seulement REMPLACER le
// contenu d'un fichier que le propriétaire a déposé et partagé avec lui en Éditeur. On remplace donc l'APK
// au plus grand numéro du dossier, et on le renomme healthy-<numéro>.apk. Aucune dépendance (Node 18+).
import { createSign } from 'node:crypto';
import { readFileSync } from 'node:fs';

const apkPath = process.env.APK_PATH;
const buildNumber = Number(process.env.BUILD_NUMBER);
const folderId = ((process.env.UPDATE_FOLDER_LINK ?? '').match(/folders\/([A-Za-z0-9_-]+)/) ?? [])[1] ?? '';
const keyJson = process.env.DRIVE_SERVICE_ACCOUNT_JSON ?? '';

function fail(message) {
  console.error(`::error::${message}`);
  process.exit(1);
}

if (!keyJson) {
  console.log('::notice::Secret DRIVE_SERVICE_ACCOUNT_JSON absent : publication sur le Drive sautée.');
  process.exit(0);
}
if (!Number.isInteger(buildNumber) || buildNumber < 1) fail('BUILD_NUMBER introuvable.');
if (!folderId) fail("L'identifiant du dossier est introuvable dans le secret UPDATE_FOLDER_LINK.");
let key;
try {
  key = JSON.parse(keyJson);
} catch {
  fail('Le secret DRIVE_SERVICE_ACCOUNT_JSON n\'est pas un JSON valide.');
}

async function accessToken() {
  const now = Math.floor(Date.now() / 1000);
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
  const unsigned = `${encode({ alg: 'RS256', typ: 'JWT' })}.${encode({
    iss: key.client_email,
    scope: 'https://www.googleapis.com/auth/drive',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  })}`;
  const signature = createSign('RSA-SHA256').update(unsigned).sign(key.private_key, 'base64url');
  const response = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: `${unsigned}.${signature}`,
    }),
  });
  if (!response.ok) fail(`Connexion du compte de service refusée (${response.status}).`);
  return (await response.json()).access_token;
}

const auth = { authorization: `Bearer ${await accessToken()}` };

const query = encodeURIComponent(`'${folderId}' in parents and trashed = false and name contains '.apk'`);
const list = await fetch(`https://www.googleapis.com/drive/v3/files?q=${query}&fields=files(id,name)&pageSize=100`, { headers: auth });
if (list.status === 404) fail('Dossier Drive introuvable : partage-le avec le compte de service (docs/architecture.md).');
if (list.status === 403) fail("L'API Google Drive est désactivée dans le projet Google Cloud, ou le dossier n'est pas partagé avec le compte de service.");
if (!list.ok) fail(`Le dossier Drive ne répond pas (${list.status}).`);

// Fichiers healthy-<numéro>.apk, du plus grand numéro au plus petit.
const apks = ((await list.json()).files ?? [])
  .map((file) => ({ ...file, number: Number((file.name.match(/^healthy-(\d+)\.apk$/) ?? [])[1]) }))
  .filter((file) => Number.isInteger(file.number))
  .sort((a, b) => b.number - a.number);
if (apks.length === 0) fail('Aucun healthy-<numéro>.apk dans le dossier : dépose-y une première fois un APK à la main (docs/architecture.md).');
const target = apks[0];
if (buildNumber <= target.number) fail(`Le dossier a déjà ${target.name} : le build ${buildNumber} n'est pas plus récent.`);

const apk = readFileSync(apkPath);
const boundary = `healthy-${Date.now()}`;
const body = Buffer.concat([
  Buffer.from(`--${boundary}\r\ncontent-type: application/json; charset=UTF-8\r\n\r\n${JSON.stringify({ name: `healthy-${buildNumber}.apk` })}\r\n`),
  Buffer.from(`--${boundary}\r\ncontent-type: application/vnd.android.package-archive\r\n\r\n`),
  apk,
  Buffer.from(`\r\n--${boundary}--`),
]);
const upload = await fetch(`https://www.googleapis.com/upload/drive/v3/files/${target.id}?uploadType=multipart&fields=id,name`, {
  method: 'PATCH',
  headers: { ...auth, 'content-type': `multipart/related; boundary=${boundary}` },
  body,
});
if (upload.status === 403) fail('Le compte de service voit le dossier mais ne peut pas le modifier : donne-lui le rôle Éditeur.');
if (!upload.ok) fail(`L'envoi de l'APK sur le Drive a échoué (${upload.status}).`);
console.log(`APK remplacé sur le Drive : ${target.name} -> healthy-${buildNumber}.apk.`);
