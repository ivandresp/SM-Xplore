const user = process.env.MONGO_APP_USER || 'reviews_app';
const password = process.env.MONGO_APP_PASSWORD;

if (!password) {
  throw new Error('MONGO_APP_PASSWORD is required');
}

const target = db.getSiblingDB('reviews');
if (!target.getUser(user)) {
  target.createUser({
    user: user,
    pwd: password,
    roles: [{ role: 'readWrite', db: 'reviews' }]
  });
}
