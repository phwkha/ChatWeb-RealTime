import { apiRequest } from './apiClient.js'

const DB_NAME = 'chatweb-e2ee'
const STORE_NAME = 'keypairs'
const DB_VERSION = 1

function openDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains(STORE_NAME)) {
        request.result.createObjectStore(STORE_NAME, { keyPath: 'username' })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

async function readRecord(username) {
  const database = await openDatabase()
  return new Promise((resolve, reject) => {
    const request = database.transaction(STORE_NAME, 'readonly').objectStore(STORE_NAME).get(username)
    request.onsuccess = () => resolve(request.result || null)
    request.onerror = () => reject(request.error)
  }).finally(() => database.close())
}

async function writeRecord(record) {
  const database = await openDatabase()
  return new Promise((resolve, reject) => {
    const request = database.transaction(STORE_NAME, 'readwrite').objectStore(STORE_NAME).put(record)
    request.onsuccess = () => resolve(record)
    request.onerror = () => reject(request.error)
  }).finally(() => database.close())
}

function bytesToBase64(value) {
  const bytes = value instanceof Uint8Array ? value : new Uint8Array(value)
  let binary = ''
  for (let index = 0; index < bytes.length; index += 1) binary += String.fromCharCode(bytes[index])
  return btoa(binary)
}

function base64ToBytes(value) {
  const binary = atob(value)
  const bytes = new Uint8Array(binary.length)
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index)
  return bytes
}

async function importPublicKey(base64) {
  return crypto.subtle.importKey(
    'spki', base64ToBytes(base64), { name: 'RSA-OAEP', hash: 'SHA-256' }, false, ['encrypt'],
  )
}

async function importPrivateKey(base64) {
  return crypto.subtle.importKey(
    'pkcs8', base64ToBytes(base64), { name: 'RSA-OAEP', hash: 'SHA-256' }, false, ['decrypt'],
  )
}

async function deriveBackupKey(password, salt) {
  const material = await crypto.subtle.importKey(
    'raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveKey'],
  )
  return crypto.subtle.deriveKey(
    { name: 'PBKDF2', salt, iterations: 250000, hash: 'SHA-256' },
    material,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  )
}

export async function getLocalKeyStatus(username) {
  const record = await readRecord(username)
  return { hasLocalKey: Boolean(record), createdAt: record?.createdAt || null }
}

export async function initializeEncryption(username) {
  const localRecord = await readRecord(username)
  if (localRecord) return { hasLocalKey: true, requiresRestore: false }
  try {
    const remotePublicKey = await lookupPublicKey(username)
    if (remotePublicKey) return { hasLocalKey: false, requiresRestore: true }
  } catch (error) {
    if (error?.status !== 404) return { hasLocalKey: false, requiresRestore: false }
  }
  await createAndPublishKeyPair(username)
  return { hasLocalKey: true, requiresRestore: false }
}

export async function createAndPublishKeyPair(username) {
  const keyPair = await crypto.subtle.generateKey(
    { name: 'RSA-OAEP', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
    true,
    ['encrypt', 'decrypt'],
  )
  const record = {
    username,
    publicKey: bytesToBase64(await crypto.subtle.exportKey('spki', keyPair.publicKey)),
    privateKey: bytesToBase64(await crypto.subtle.exportKey('pkcs8', keyPair.privateKey)),
    createdAt: new Date().toISOString(),
  }
  await writeRecord(record)
  await apiRequest('/api/keys/public-key', { method: 'POST', body: { publicKey: record.publicKey } })
  return record
}

export async function publishLocalPublicKey(username) {
  const record = await readRecord(username)
  if (!record) throw new Error('Thiết bị này chưa có cặp khóa mã hóa.')
  return apiRequest('/api/keys/public-key', { method: 'POST', body: { publicKey: record.publicKey } })
}

export async function lookupPublicKey(username) {
  const response = await apiRequest(`/api/keys/public-key/${encodeURIComponent(username)}`)
  return response?.data || ''
}

export async function backupLocalKeyPair(username, password) {
  const record = await readRecord(username)
  if (!record) throw new Error('Hãy tạo khóa trên thiết bị trước khi sao lưu.')
  if (!password) throw new Error('Vui lòng nhập mật khẩu bảo vệ bản sao lưu.')
  const salt = crypto.getRandomValues(new Uint8Array(16))
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const backupKey = await deriveBackupKey(password, salt)
  const encrypted = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv }, backupKey,
    new TextEncoder().encode(JSON.stringify({ publicKey: record.publicKey, privateKey: record.privateKey })),
  )
  const key = JSON.stringify({ version: 1, salt: bytesToBase64(salt), iv: bytesToBase64(iv), data: bytesToBase64(encrypted) })
  return apiRequest('/api/keys/rsa', { method: 'POST', body: { key } })
}

export async function restoreKeyPair(username, password) {
  if (!password) throw new Error('Vui lòng nhập mật khẩu của bản sao lưu.')
  const response = await apiRequest('/api/keys/rsa')
  const encryptedBackup = response?.data?.privateKey
  if (!encryptedBackup) throw new Error('Không tìm thấy bản sao lưu khóa trên máy chủ.')
  const backup = JSON.parse(encryptedBackup)
  const backupKey = await deriveBackupKey(password, base64ToBytes(backup.salt))
  const decrypted = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: base64ToBytes(backup.iv) }, backupKey, base64ToBytes(backup.data),
  )
  const keyPair = JSON.parse(new TextDecoder().decode(decrypted))
  const record = { username, ...keyPair, createdAt: new Date().toISOString() }
  await writeRecord(record)
  await publishLocalPublicKey(username)
  return record
}

export async function encryptMessageContent(content, senderUsername, recipientUsername) {
  const localRecord = await readRecord(senderUsername)
  if (!localRecord || !content) return null
  const [senderPublicValue, recipientPublicValue] = await Promise.all([
    lookupPublicKey(senderUsername),
    lookupPublicKey(recipientUsername),
  ])
  if (!senderPublicValue || !recipientPublicValue) return null
  const [senderPublicKey, recipientPublicKey] = await Promise.all([
    importPublicKey(senderPublicValue), importPublicKey(recipientPublicValue),
  ])
  const aesKey = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt', 'decrypt'])
  const rawAesKey = await crypto.subtle.exportKey('raw', aesKey)
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const ciphertext = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv }, aesKey, new TextEncoder().encode(content),
  )
  const [wrappedKeySender, wrappedKeyRecipient] = await Promise.all([
    crypto.subtle.encrypt({ name: 'RSA-OAEP' }, senderPublicKey, rawAesKey),
    crypto.subtle.encrypt({ name: 'RSA-OAEP' }, recipientPublicKey, rawAesKey),
  ])
  return {
    content: bytesToBase64(ciphertext),
    iv: bytesToBase64(iv),
    wrappedKeySender: bytesToBase64(wrappedKeySender),
    wrappedKeyRecipient: bytesToBase64(wrappedKeyRecipient),
  }
}

export async function decryptMessage(message, currentUsername) {
  if (!message?.iv || !message?.content || !currentUsername) return message
  const localRecord = await readRecord(currentUsername)
  if (!localRecord) return { ...message, content: '🔒 Tin nhắn được mã hóa trên thiết bị khác.', decryptionFailed: true }
  try {
    const privateKey = await importPrivateKey(localRecord.privateKey)
    const wrappedKey = message.sender === currentUsername ? message.wrappedKeySender : message.wrappedKeyRecipient
    const rawAesKey = await crypto.subtle.decrypt({ name: 'RSA-OAEP' }, privateKey, base64ToBytes(wrappedKey))
    const aesKey = await crypto.subtle.importKey('raw', rawAesKey, { name: 'AES-GCM' }, false, ['decrypt'])
    const plaintext = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: base64ToBytes(message.iv) }, aesKey, base64ToBytes(message.content),
    )
    return { ...message, content: new TextDecoder().decode(plaintext), encrypted: true }
  } catch {
    return { ...message, content: '🔒 Không thể giải mã tin nhắn này.', decryptionFailed: true }
  }
}

export async function decryptMessages(messages, currentUsername) {
  return Promise.all((messages || []).map((message) => decryptMessage(message, currentUsername)))
}
