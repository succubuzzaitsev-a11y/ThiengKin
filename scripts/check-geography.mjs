import { readFileSync } from 'node:fs';
const d = JSON.parse(readFileSync('D:/thiengKin/data/thailand-geography.json', 'utf8'));
console.log('Regions:');
for (const r of d.regions) {
  console.log(`  ${r.nameEn}  id=${r.id}  area=${r.areaSqkm.toFixed(0)} km²`);
}
console.log('\nSample provinces:');
for (const p of d.provinces.slice(0, 5)) {
  console.log(`  ${p.nameEn.padEnd(25)} id=${p.id.padEnd(25)} code=${p.code}  region=${p.regionNesdb}`);
}
console.log('\nSample districts:');
for (const d2 of d.districts.slice(0, 5)) {
  console.log(`  ${d2.nameEn.padEnd(25)} id=${d2.id.padEnd(25)} code=${d2.code}  pro=${d2.provinceId}`);
}
console.log('\nFile size:', (JSON.stringify(d).length / 1024).toFixed(1), 'KB');
