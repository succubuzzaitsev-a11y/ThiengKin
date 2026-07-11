import json

with open(r'android\app\src\main\assets\chiangmai-restaurants-final.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

# Chiang Mai area bounds (extended: lat 17.5-20.0, lng 97.8-99.5)
# Includes: เชียงใหม่, ลำพูน, แม่ริม, หางดง, สันทราย, etc.
LAT_MIN, LAT_MAX = 17.5, 20.0
LNG_MIN, LNG_MAX = 97.8, 99.5

in_cm = []
out_cm = []
for r in data['restaurants']:
    lat, lng = r['lat'], r['lng']
    if LAT_MIN <= lat <= LAT_MAX and LNG_MIN <= lng <= LNG_MAX:
        in_cm.append(r)
    else:
        out_cm.append(r)

print('Total:', len(data['restaurants']))
print('In Chiang Mai area:', len(in_cm))
print('OUT of bounds:', len(out_cm))
print()
print('=== Out of bounds (all) ===')
for r in out_cm:
    src = r.get('source', '?')
    rid = r['id'][:25]
    name = r['name'][:50]
    lat = r['lat']
    lng = r['lng']
    dist = r.get('district', '')
    print(f'  [{src:10s}] {rid:25s} lat={lat:8.4f} lng={lng:9.4f}  {name}  | district={dist}')

print()
print('=== In bounds source breakdown ===')
manual_in = sum(1 for r in in_cm if r.get('source') == 'manual')
fsq_in = sum(1 for r in in_cm if r.get('source') != 'manual')
print('  manual:', manual_in, 'foursquare:', fsq_in)
