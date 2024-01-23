module.exports = {
    timestampToMMSS: function(ms) {
        return `${ms >= 60 * 1e3 ? Math.floor(ms / (60 * 1e3)) + 'm ' : ''}${Math.floor((ms % (60 * 1e3)) / 1e3)}s`;
    }
}
