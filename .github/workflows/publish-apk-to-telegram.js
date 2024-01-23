module.exports = ({github, context}) => {
  const fs = require('fs');
  const TelegramBot = require('node-telegram-bot-api');
  const token = process.env.TELEGRAM_BOT_TOKEN;
  const chatId = process.env.TELEGRAM_CHAT_ID;
  const bot = new TelegramBot(token);
  const apkName = fs.readdirSync('./app/build/outputs/apk/debug/').filter(fn => fn.endsWith('.apk'))[0];
  const apk = fs.readFileSync('./app/build/outputs/apk/debug/' + apkName);
  const caption = `${process.env.BRANCH_NAME}`;
  bot.sendDocument(chatId, apk, {caption: caption}, {filename: apkName, contentType: 'application/vnd.android.package-archive'})
    .then(response => {
      console.log("File " + apk + " successfully sent to Telegram");
    })
    .catch(err => {
      console.error("Failed to send file:", err);
    });
}