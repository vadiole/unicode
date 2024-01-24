module.exports = ({github, context}) => {
  process.env["NTBA_FIX_350"] = 1;
  const fs = require('fs');
  const formatter = require('./formatter.js');
  const TelegramBot = require('node-telegram-bot-api');
  const token = process.env.TELEGRAM_BOT_TOKEN;
  const chatId = process.env.TELEGRAM_CHAT_ID;
  const bot = new TelegramBot(token);
  const thumbnail = fs.readFileSync('./assets/logo-320.jpg');
  var file_id = "";
  const thumbnailOptions = {filename: 'thumbnail.jpg', contentType: 'image/jpeg'};
  bot.sendPhoto(chatId, thumbnail, {}, thumbnailOptions)
      .then(response => {
        file_id = response.photo.file_id;
        console.log("Thumbnail successfully sent to Telegram, file_id: " + file_id);
      })
      .catch(err => {
        console.error("Failed to send thumbnail:", err);
      });
  const apkName = fs.readdirSync('./app/build/outputs/apk/debug/').filter(fn => fn.endsWith('.apk'))[0];
  const apk = fs.readFileSync('./app/build/outputs/apk/debug/' + apkName);
  const duration = Date.now() - process.env.START_TIMESTAMP;
  const messageOptions = {caption: `${process.env.BRANCH_NAME}\n[${formatter.timestampToMMSS(duration)}]`, thumbnail: file_id};
  const fileOptions = {filename: apkName, contentType: 'application/vnd.android.package-archive', thumbnail: file_id};
  bot.sendDocument(chatId, apk, messageOptions, fileOptions)
    .then(response => {
      console.log("File " + apkName + " successfully sent to Telegram.\nCaption: " + messageOptions.caption);
    })
    .catch(err => {
      console.error("Failed to send file:", err);
    });
}
