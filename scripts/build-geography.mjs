#!/usr/bin/env node
/**
 * Build Thailand geography JSON from raw GeoJSON
 *
 * Input:  data/raw-provinces.geojson
 *         data/raw-districts.geojson
 *         data/raw-reg-nesdb.geojson
 *         data/raw-reg-royin.geojson
 *
 * Output: data/thailand-geography.json
 *         {
 *           regions: [{ id, nameEn, nameTh, bbox: {s,w,n,e}, areaSqkm }],
 *           provinces: [{ id, nameTh, nameEn, regionNesdb, regionRoyin, code, bbox, centroid, areaSqkm }],
 *           districts: [{ id, provinceId, nameTh, nameEn, code, bbox, centroid, areaSqkm }]
 *         }
 *
 * Run: node scripts/build-geography.mjs
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const DATA_DIR = join(__dirname, '..', 'data');

/**
 * Recursively walk GeoJSON coordinates and return all [lng, lat] points.
 * Geometry types: Point, LineString, Polygon, MultiPolygon, etc.
 * Coordinates structure varies by type — we walk all number pairs.
 */
function extractPoints(coords) {
  const points = [];
  function walk(c) {
    if (typeof c[0] === 'number') {
      points.push(c);
      return;
    }
    for (const item of c) walk(item);
  }
  walk(coords);
  return points;
}

/**
 * Compute bbox {s, w, n, e} from a GeoJSON geometry.
 * Coordinates are [lng, lat] in GeoJSON spec.
 */
function computeBbox(geometry) {
  const points = extractPoints(geometry.coordinates);
  let minLng = Infinity, minLat = Infinity;
  let maxLng = -Infinity, maxLat = -Infinity;
  let sumLng = 0, sumLat = 0;
  for (const [lng, lat] of points) {
    if (lng < minLng) minLng = lng;
    if (lng > maxLng) maxLng = lng;
    if (lat < minLat) minLat = lat;
    if (lat > maxLat) maxLat = lat;
    sumLng += lng;
    sumLat += lat;
  }
  const n = points.length || 1;
  return {
    s: +minLat.toFixed(6),
    w: +minLng.toFixed(6),
    n: +maxLat.toFixed(6),
    e: +maxLng.toFixed(6),
    centroidLat: +(sumLat / n).toFixed(6),
    centroidLng: +(sumLng / n).toFixed(6),
  };
}

/**
 * Make a stable lowercase id from Thai/English name.
 * E.g. "Chiang Mai" -> "chiang_mai", "Phra Nakhon Si Ayutthaya" -> "phra_nakhon_si_ayutthaya"
 */
function makeId(nameEn) {
  return nameEn
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
}

function loadGeoJson(name) {
  const path = join(DATA_DIR, name);
  const raw = readFileSync(path, 'utf8');
  return JSON.parse(raw);
}

function buildRegions() {
  // Use NESDB regions (6 regions — the more common grouping for statistics)
  const raw = loadGeoJson('raw-reg-nesdb.geojson');
  const regions = [];
  for (const f of raw.features) {
    const nameEn = f.properties.reg_nesdb;
    const bbox = computeBbox(f.geometry);
    regions.push({
      id: makeId(nameEn),
      nameEn,
      nameTh: null,  // not in source
      bbox: { s: bbox.s, w: bbox.w, n: bbox.n, e: bbox.e },
      centroid: { lat: bbox.centroidLat, lng: bbox.centroidLng },
      areaSqkm: f.properties.area_sqkm,
    });
  }
  // Sort: north→south, then name
  return regions.sort((a, b) => a.nameEn.localeCompare(b.nameEn));
}

function buildProvinces() {
  const raw = loadGeoJson('raw-provinces.geojson');
  const provinces = [];
  for (const f of raw.features) {
    const p = f.properties;
    const bbox = computeBbox(f.geometry);
    provinces.push({
      id: makeId(p.pro_en),
      code: p.pro_code,                    // official 2-digit code
      nameTh: p.pro_th,
      nameEn: p.pro_en,
      regionNesdb: makeId(p.reg_nesdb),
      regionRoyin: makeId(p.reg_royin),
      bbox: { s: bbox.s, w: bbox.w, n: bbox.n, e: bbox.e },
      centroid: { lat: bbox.centroidLat, lng: bbox.centroidLng },
      areaSqkm: p.area_sqkm,
      perimeterKm: p.perimeter,
    });
  }
  return provinces.sort((a, b) => a.nameEn.localeCompare(b.nameEn));
}

function buildDistricts() {
  const raw = loadGeoJson('raw-districts.geojson');
  const districts = [];
  for (const f of raw.features) {
    const p = f.properties;
    const bbox = computeBbox(f.geometry);
    const provinceId = makeId(p.pro_en);
    districts.push({
      id: makeId(p.amp_en),
      code: p.amp_code,                    // official 4-digit code
      nameTh: p.amp_th,
      nameEn: p.amp_en,
      provinceId,
      regionNesdb: makeId(p.reg_nesdb),
      regionRoyin: makeId(p.reg_royin),
      bbox: { s: bbox.s, w: bbox.w, n: bbox.n, e: bbox.e },
      centroid: { lat: bbox.centroidLat, lng: bbox.centroidLng },
      areaSqkm: p.area_sqkm,
      perimeterKm: p.perimeter,
    });
  }
  return districts.sort((a, b) => a.nameEn.localeCompare(b.nameEn));
}

// === Run ===
console.log('[build-geography] Loading GeoJSON files...');
const t0 = Date.now();

const regions = buildRegions();
const provinces = buildProvinces();
const districts = buildDistricts();

console.log(`  regions:   ${regions.length} (expected 6)`);
console.log(`  provinces: ${provinces.length} (expected 77)`);
console.log(`  districts: ${districts.length} (expected ~928)`);

if (provinces.length !== 77) {
  console.warn(`  ⚠️  provinces count is ${provinces.length}, expected 77 — check data`);
}
if (regions.length !== 6) {
  console.warn(`  ⚠️  regions count is ${regions.length}, expected 6 — check data`);
}

// Sanity check: known province should be present
const known = ['Bangkok', 'Chiang Mai', 'Phuket', 'Songkhla'];
for (const name of known) {
  const found = provinces.find(p => p.nameEn === name);
  console.log(`  ${found ? '✓' : '✗'} ${name}: ${found ? `id=${found.id} code=${found.code}` : 'MISSING'}`);
}

// Sanity: every district has a matching province
const provinceIds = new Set(provinces.map(p => p.id));
const orphanDistricts = districts.filter(d => !provinceIds.has(d.provinceId));
if (orphanDistricts.length > 0) {
  console.warn(`  ⚠️  ${orphanDistricts.length} districts have no matching province:`);
  for (const d of orphanDistricts.slice(0, 5)) {
    console.warn(`     - ${d.nameEn} (pro_en=${d.provinceId})`);
  }
}

const out = {
  meta: {
    version: 1,
    generatedAt: new Date().toISOString(),
    source: 'chingchai/OpenGISData-Thailand (provinces.geojson, districts.geojson, reg_nesdb.geojson)',
    license: 'Open data — verify usage terms with source',
    counts: { regions: regions.length, provinces: provinces.length, districts: districts.length },
  },
  regions,
  provinces,
  districts,
};

const outPath = join(DATA_DIR, 'thailand-geography.json');
writeFileSync(outPath, JSON.stringify(out, null, 2), 'utf8');
const sizeKB = (JSON.stringify(out).length / 1024).toFixed(1);
console.log(`\n[build-geography] Wrote ${outPath} (${sizeKB} KB) in ${Date.now() - t0}ms`);
