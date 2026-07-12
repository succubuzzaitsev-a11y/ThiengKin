// osm-nearest.mjs — show N nearest OSM places to a target lat/lng
// Usage: node scripts/osm-nearest.mjs <osm-file> <lat> <lng> [N]

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, '..');

const args = process.argv.slice(2);
if (args.length < 3 || args.includes('--help') || args.includes('-h')) {
  console.log('Usage: node scripts/osm-nearest.mjs <osm-file> <lat> <lng> [N=8]');
  console.log('Example: node scripts/osm-nearest.mjs data/osm-mueang-nonthaburi.json 13.82 100.49');
  process.exit(args.length ? 0 : 1);
}

const file = path.isAbsolute(args[0]) ? args[0] : path.join(ROOT, args[0]);
const target = { lat: parseFloat(args[1]), lng: parseFloat(args[2]) };
const N = parseInt(args[3] || '8', 10);

const d = JSON.parse(fs.readFileSync(file, 'utf8'));

function haversine(lat, lon) {
  const R = 6371;
  const dLat = (lat - target.lat) * Math.PI / 180;
  const dLon = (lon - target.lng) * Math.PI / 180;
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos(target.lat * Math.PI / 180) * Math.cos(lat * Math.PI / 180) * Math.sin(dLon / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

const named = d.elements
  .filter(e => e.tags?.name)
  .map(e => {
    const lat = e.lat ?? e.center?.lat;
    const lon = e.lon ?? e.center?.lon;
    return lat && lon ? { ...e, _lat: lat, _lon: lon, _dist: haversine(lat, lon) } : null;
  })
  .filter(Boolean)
  .sort((a, b) => a._dist - b._dist)
  .slice(0, N);

console.log(`Top ${named.length} nearest OSM places to (${target.lat}, ${target.lng})`);
console.log(`Source: ${file}\n`);

for (const e of named) {
  const t = e.tags;
  const amenity = t.amenity || '?';
  const cuisine = t.cuisine ? ` [${t.cuisine}]` : '';
  const th = t['name:th'] && t['name:th'] !== t.name ? ` (TH: ${t['name:th']})` : '';
  const addr = [t['addr:street'], t['addr:suburb']].filter(Boolean).join(' ');
  console.log(`  ${e._dist.toFixed(2)} km  ${t.name}${th}`);
  console.log(`            ${amenity}${cuisine}  |  ${e._lat.toFixed(5)}, ${e._lon.toFixed(5)}${addr ? '  |  ' + addr : ''}`);
}
