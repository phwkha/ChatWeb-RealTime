/**
 * MongoDB Initialization Script
 * Auto-mounted to /docker-entrypoint-initdb.d/init-mongo.js
 * Initializes collections and compound indexes for ChatWeb.
 */

const dbName = (typeof process !== 'undefined' && process.env && process.env.MONGO_DB) ? process.env.MONGO_DB : 'chatweb';
const targetDb = db.getSiblingDB(dbName);

print('Initializing MongoDB collections and indexes for database: ' + dbName);

// 1. Collection 'messages'
targetDb.createCollection('messages');
targetDb.messages.createIndex(
    { conversationId: 1, messageType: 1, timestamp: -1 },
    { name: 'conv_msg_time_idx' }
);
targetDb.messages.createIndex(
    { recipient: 1, messageType: 1, isDeleted: 1, sender: 1 },
    { name: 'unread_msg_idx' }
);
targetDb.messages.createIndex(
    { conversationId: 1, messageType: 1, isDeleted: 1, timestamp: -1 },
    { name: 'conv_content_time_idx' }
);
targetDb.messages.createIndex(
    { sender: 1 },
    { name: 'sender_idx' }
);

// 2. Collection 'read_receipts'
targetDb.createCollection('read_receipts');
targetDb.read_receipts.createIndex(
    { conversationId: 1, username: 1 },
    { name: 'conv_user_idx', unique: true }
);
targetDb.read_receipts.createIndex(
    { conversationId: 1 },
    { name: 'conversationId_idx' }
);
targetDb.read_receipts.createIndex(
    { username: 1 },
    { name: 'username_idx' }
);

// 3. Collection 'system_message'
targetDb.createCollection('system_message');
targetDb.system_message.createIndex(
    { expiresAt: 1 },
    { name: 'expiresAt_ttl_idx', expireAfterSeconds: 0 }
);

print('MongoDB collections and compound indexes initialized successfully for DB: ' + dbName);
