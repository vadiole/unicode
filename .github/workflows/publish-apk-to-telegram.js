module.exports = ({github, context}) => {
  process.env["NTBA_FIX_350"] = 1;
  const fs = require('fs');
  const formatter = require('./formatter.js');
  const TelegramBot = require('node-telegram-bot-api');
  const token = process.env.TELEGRAM_BOT_TOKEN;
  const chatId = process.env.TELEGRAM_CHAT_ID;
  const bot = new TelegramBot(token);
  const apkName = fs.readdirSync('./app/build/outputs/apk/debug/').filter(fn => fn.endsWith('.apk'))[0];
  const apk = fs.readFileSync('./app/build/outputs/apk/debug/' + apkName);
  const startTimestamp = process.env.START_TIMESTAMP;
  console.log("Start timestamp: " + startTimestamp);
  const nowTimestamp = Date.now();
  console.log("Now timestamp: " + nowTimestamp);
  const duration = nowTimestamp - startTimestamp;
  console.log("Duration: " + duration);
  console.log("Duration formatted: " + formatter.timestampToMMSS(duration));
  const messageOptions = {caption: `Branch: ${process.env.BRANCH_NAME}\nBuild time: ${formatter.timestampToMMSS(duration)}`}
  const fileOptions = {filename: apkName, contentType: 'application/vnd.android.package-archive'};
  bot.sendDocument(chatId, apk, messageOptions, fileOptions)
    .then(response => {
      console.log("File " + apk + " successfully sent to Telegram");
    })
    .catch(err => {
      console.error("Failed to send file:", err);
    });
}
