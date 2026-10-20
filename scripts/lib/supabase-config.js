// Reads Supabase connection values from the environment, falling back to local.properties.
// Keys mirror the Gradle BuildConfig fields used by the app (see app/build.gradle.kts).
const fs = require('fs');
const path = require('path');

const LOCAL_PROPS = path.join(__dirname, '..', '..', 'local.properties');

function readLocalProps() {
    if (!fs.existsSync(LOCAL_PROPS)) return {};
    const props = {};
    for (const line of fs.readFileSync(LOCAL_PROPS, 'utf8').split(/\r?\n/)) {
        const m = line.match(/^\s*([A-Za-z0-9_.-]+)\s*[=:]\s*(.*)$/);
        if (!m || /^\s*#/.test(line)) continue;
        // Java properties escapes: \: \= \\ and leading ! or = separators
        props[m[1]] = m[2].trim().replace(/\\(.)/g, '$1');
    }
    return props;
}

function requireValue(key) {
    const value = process.env[key] || readLocalProps()[key];
    if (!value) {
        throw new Error(
            `${key} is not set. Add it to ${LOCAL_PROPS} (see local.properties.template) ` +
            `or export it before running this script.`
        );
    }
    return value;
}

module.exports = { requireValue };
