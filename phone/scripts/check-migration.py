"""Run committed v2 -> v3 SQL with SQLite; this is not an Android Room runtime test."""
import json
import pathlib
import re
import sqlite3

root = pathlib.Path(__file__).resolve().parents[2]
schemas = root / 'phone/app/schemas/com.dhyan.app.data.DhyanDatabase'
old = json.loads((schemas / '2.json').read_text())['database']
new = json.loads((schemas / '3.json').read_text())['database']
db = sqlite3.connect(':memory:')
for entity in old['entities']:
    db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    for index in entity.get('indices', []):
        db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
db.execute("INSERT INTO events (ts,kind,app_cat,surface,duration_min,sender_hash,notif_cat,scroll_count,extras_json) VALUES (1234,'screen_on','system','other',0,NULL,NULL,0,NULL)")
source = (root / 'phone/app/src/main/java/com/dhyan/app/data/DhyanDatabase.kt').read_text()
block = source.split('val MIGRATION_2_3 =', 1)[1].split('fun getInstance', 1)[0]
sql = [a or b for a, b in re.findall(r'db\.execSQL\((?:"""(.*?)"""\.trimIndent\(\)|"([^"\n]*)")\)', block, re.S)]
assert len(sql) == 9, len(sql)
for statement in sql:
    db.execute(statement)
assert db.execute('SELECT ts,kind,app_cat,event_id,seq,device_id,schema_version FROM events').fetchone() == (1234, 'screen_on', 'system', None, 0, 'phone', '1.1.0')
for entity in new['entities']:
    actual = {r[1]: (r[2], bool(r[3])) for r in db.execute('PRAGMA table_info("' + entity['tableName'] + '")')}
    expected = {f['columnName']: (f['affinity'], f.get('notNull', False)) for f in entity['fields']}
    assert actual == expected, (entity['tableName'], actual, expected)
print(f'SQLite migration v2 -> v3: PASS; {len(sql)} DDL statements; existing row preserved; {len(new["entities"])} table column layouts match exported v3 schema')
print('Room runtime migration validation and target-device upgrade: not verified on target')
